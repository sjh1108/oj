#!/usr/bin/env bash
# Single-box, one-JVM-per-box deploy for the algoj API (no-overlap).
#
# Used by the two-box redundancy setup (OJ + EOJ, see deploy/redundancy.md).
# A rolling orchestrator drains THIS box at the nginx layer
# (deploy/nginx/render-upstream.sh) BEFORE calling this script, so the other box
# serves 100% of traffic and the brief per-box downtime here is invisible to
# users. Because the box is drained, we can safely stop the old JVM before
# booting the new one — one JVM at a time, no swap thrash.
#
# This script does NOT touch nginx (the orchestrator owns the upstream). It only
# swaps the API container on a fixed port and health-checks it locally.
#
# Per-box invocation:
#   EOJ (remote API box):  PORT=8080 PUBLISH_ADDR=0.0.0.0    bash deploy-api-single.sh
#       → 0.0.0.0 so OJ's nginx can reach it over the private network;
#         the EOJ security group must restrict :8080 to OJ's private IP.
#   OJ  (API + nginx box): PORT=8081 PUBLISH_ADDR=127.0.0.1  bash deploy-api-single.sh
#       → loopback only; nginx-internal already owns 127.0.0.1:8080, so the API
#         listens on 8081 and nginx upstream points at 127.0.0.1:8081.
#
# Requires: docker, external DB (RDS via DB_HOST in .env), RabbitMQ + Judge0
# reachable via .env (RABBITMQ_HOST / JUDGE0_URL, both on the JJ box).
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/algoj}"
ENV_FILE="${ENV_FILE:-$APP_DIR/.env}"
IMAGE="${IMAGE:-ghcr.io/sjh1108/oj-api:latest}"
PORT="${PORT:-8080}"                       # host port the API listens on
PUBLISH_ADDR="${PUBLISH_ADDR:-0.0.0.0}"    # bind addr; firewall/SG restricts external reach
NAME="${NAME:-algoj-api}"
PREV="${NAME}-prev"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-200}"    # seconds to wait for new container health
DRAIN_TIMEOUT="${DRAIN_TIMEOUT:-60}"       # graceful stop window for the old container
MEM_THRESHOLD_MB="${MEM_THRESHOLD_MB:-700}"

log() { echo "[deploy-single] $*"; }

# Heap cap for the small box (same rationale as deploy-api.sh): without -Xmx the
# JVM grabs ~25% of host RAM. SerialGC + C1-only JIT keep the native footprint
# small and speed cold-boot on a memory-tight box. Explicit JAVA_OPTS overrides.
STARTUP_OPTS="-XX:+UseSerialGC -XX:TieredStopAtLevel=1"
java_opts="${JAVA_OPTS:-}"
if [ -z "$java_opts" ]; then
  java_opts="-Xms128m -Xmx300m $STARTUP_OPTS"
  avail_mb="$(free -m | awk '/^Mem:/ {print $7}')"
  if [ -n "$avail_mb" ] && [ "$avail_mb" -lt "$MEM_THRESHOLD_MB" ]; then
    java_opts="-Xms128m -Xmx256m $STARTUP_OPTS"
    log "low memory (${avail_mb}MB avail < ${MEM_THRESHOLD_MB}MB) → JAVA_OPTS=$java_opts"
  else
    log "heap capped for small box → JAVA_OPTS=$java_opts"
  fi
fi

log "deploying $NAME on ${PUBLISH_ADDR}:${PORT} ($IMAGE)"
docker pull "$IMAGE"

# no-overlap: stop the old JVM (freeing memory) before booting the new one. Keep
# it renamed as $PREV so a failed boot can roll back to the last good container.
docker rm -f "$PREV" >/dev/null 2>&1 || true
if docker ps -a --format '{{.Names}}' | grep -qx "$NAME"; then
  log "stopping current $NAME (brief downtime; box already drained at nginx)"
  docker stop --time "$DRAIN_TIMEOUT" "$NAME" >/dev/null 2>&1 || true
  docker rename "$NAME" "$PREV"
fi

run_args=(
  -d
  --name "$NAME"
  --restart unless-stopped
  --env-file "$ENV_FILE"
  # DB_HOST/DB_PORT, JUDGE0_URL and RABBITMQ_HOST all come from .env (RDS + JJ).
  -p "${PUBLISH_ADDR}:${PORT}:8080"
)
if [ -n "$java_opts" ]; then
  run_args+=(-e "JAVA_OPTS=$java_opts")
fi
docker run "${run_args[@]}" "$IMAGE"

log "waiting for health on 127.0.0.1:${PORT} (timeout ${HEALTH_TIMEOUT}s)..."
healthy=0
for _ in $(seq 1 "$HEALTH_TIMEOUT"); do
  if curl -fsS "http://127.0.0.1:${PORT}/api/health" >/dev/null 2>&1; then
    healthy=1; break
  fi
  sleep 1
done

if [ "$healthy" -ne 1 ]; then
  log "ERROR: $NAME not healthy — rolling back to previous container"
  docker logs --tail 50 "$NAME" 2>&1 || true
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  if docker ps -a --format '{{.Names}}' | grep -qx "$PREV"; then
    log "rollback: restoring $PREV → $NAME"
    docker rename "$PREV" "$NAME"
    docker start "$NAME" >/dev/null 2>&1 || true
  fi
  exit 1
fi

log "$NAME healthy."
docker rm -f "$PREV" >/dev/null 2>&1 || true
docker image prune -f >/dev/null 2>&1 || true
log "deploy complete → $NAME live on ${PUBLISH_ADDR}:${PORT}."
