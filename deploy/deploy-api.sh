#!/usr/bin/env bash
# Blue-green deploy for the algoj API on a single host behind nginx.
#
# Brings a new container up on the inactive port (8081/8082), waits for
# /api/health, flips the nginx upstream, then drains the old container.
# Safe rollback: if the new container never becomes healthy, the old one keeps
# serving and the script exits non-zero without touching nginx.
#
# Run on the box (CD does this over SSH):
#   IMAGE=ghcr.io/sjh1108/oj-api:latest bash /opt/algoj/deploy-api.sh
#
# Requires: docker, MySQL running via docker-compose.prod.yml, the nginx files
# from deploy/nginx/ installed in /etc/nginx/conf.d, and passwordless sudo for
# `nginx` + writing the upstream conf (see deploy/README.md).
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/algoj}"
ENV_FILE="${ENV_FILE:-$APP_DIR/.env}"
IMAGE="${IMAGE:-ghcr.io/sjh1108/oj-api:latest}"
NETWORK="${NETWORK:-algoj_default}"
UPSTREAM_CONF="${UPSTREAM_CONF:-/etc/nginx/conf.d/algoj-upstream.conf}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-algoj-mysql}"
BLUE_PORT=8081
GREEN_PORT=8082
# On this small (≈2GB) box a cold JVM boot can crawl when memory is tight and
# the box is swapping — during the blue-green overlap two JVMs run at once. 90s
# was too short and failed a deploy mid-boot; 150s gives startup room to finish.
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-150}"   # seconds to wait for new container health
DRAIN_TIMEOUT="${DRAIN_TIMEOUT:-60}"     # graceful stop window for the old container
MEM_THRESHOLD_MB="${MEM_THRESHOLD_MB:-700}"  # below this available RAM → shrink heap

log() { echo "[deploy-api] $*"; }

# 1. Determine active color from the current upstream port; pick the inactive one.
active_port="$(grep -oE '127\.0\.0\.1:[0-9]+' "$UPSTREAM_CONF" 2>/dev/null \
  | grep -oE '[0-9]+$' | head -1 || true)"
if [ "$active_port" = "$BLUE_PORT" ]; then
  new_color=green; new_port=$GREEN_PORT; old_color=blue
else
  new_color=blue; new_port=$BLUE_PORT; old_color=green
fi
new_name="algoj-api-$new_color"
old_name="algoj-api-$old_color"
log "active=${active_port:-none} → deploying $new_color on $new_port ($IMAGE)"

# 2. Memory pre-flight: the box is small (≈2GB) and blue-green briefly runs two
#    JVMs at once. Without an explicit -Xmx the JVM grabs ~25% of host RAM
#    (~500MB) each, so the overlap forces the whole box into swap and the pages
#    never drain. Always cap the heap; shrink further only when available RAM is
#    already tight at deploy time. An explicit JAVA_OPTS still overrides both.
java_opts="${JAVA_OPTS:-}"
if [ -z "$java_opts" ]; then
  java_opts="-Xms128m -Xmx300m"
  avail_mb="$(free -m | awk '/^Mem:/ {print $7}')"
  if [ -n "$avail_mb" ] && [ "$avail_mb" -lt "$MEM_THRESHOLD_MB" ]; then
    java_opts="-Xms128m -Xmx256m"
    log "low memory (${avail_mb}MB avail < ${MEM_THRESHOLD_MB}MB) → JAVA_OPTS=$java_opts"
  else
    log "heap capped for small box → JAVA_OPTS=$java_opts"
  fi
fi

# 3. Pull the new image and make sure MySQL is healthy before starting.
docker pull "$IMAGE"
log "waiting for $MYSQL_CONTAINER healthy..."
for _ in $(seq 1 30); do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$MYSQL_CONTAINER" 2>/dev/null || echo missing)"
  [ "$status" = "healthy" ] && break
  sleep 2
done

# 4. Start the new container on the inactive port.
docker rm -f "$new_name" >/dev/null 2>&1 || true
run_args=(
  -d
  --name "$new_name"
  --restart unless-stopped
  --env-file "$ENV_FILE"
  -e DB_HOST=mysql -e DB_PORT=3306
  -e RABBITMQ_HOST=rabbitmq
  -e JUDGE0_URL=http://host.docker.internal:2358
  --add-host host.docker.internal:host-gateway
  --network "$NETWORK"
  -p "127.0.0.1:${new_port}:8080"
)
if [ -n "$java_opts" ]; then
  run_args+=(-e "JAVA_OPTS=$java_opts")
fi
docker run "${run_args[@]}" "$IMAGE"

# 5. Wait for the new container to report healthy (DB-aware /api/health).
log "waiting for health on 127.0.0.1:${new_port} (timeout ${HEALTH_TIMEOUT}s)..."
healthy=0
for _ in $(seq 1 "$HEALTH_TIMEOUT"); do
  if curl -fsS "http://127.0.0.1:${new_port}/api/health" >/dev/null 2>&1; then
    healthy=1; break
  fi
  sleep 1
done
if [ "$healthy" -ne 1 ]; then
  log "ERROR: $new_name not healthy — keeping $old_color active (rollback)"
  docker logs --tail 50 "$new_name" 2>&1 || true
  docker rm -f "$new_name" >/dev/null 2>&1 || true
  exit 1
fi
log "$new_name healthy."

# 6. Flip the nginx upstream to the new port and reload gracefully.
printf 'upstream algoj_api {\n    server 127.0.0.1:%s;\n}\n' "$new_port" \
  | sudo tee "$UPSTREAM_CONF" >/dev/null
sudo nginx -t
sudo nginx -s reload
log "nginx now routing to $new_color (port $new_port)."

# 7. Drain & remove the old container, then prune dangling images.
if docker ps -a --format '{{.Names}}' | grep -qx "$old_name"; then
  log "draining $old_name (${DRAIN_TIMEOUT}s)..."
  docker stop --time "$DRAIN_TIMEOUT" "$old_name" >/dev/null 2>&1 || true
  docker rm "$old_name" >/dev/null 2>&1 || true
fi
docker image prune -f >/dev/null 2>&1 || true
log "deploy complete → $new_color is live."
