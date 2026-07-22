#!/usr/bin/env bash
# Rolling zero-downtime deploy across the two API boxes (OJ + EOJ).
#
# Runs ON OJ, which hosts nginx (the load balancer) and acts as the conductor.
# It drains one box at a time at the nginx layer (render-upstream.sh) so the
# OTHER box serves 100% while the drained box's container is replaced — one JVM
# per box, no overlap, no user-visible downtime.
#
#   EOJ first (OJ serves) → OJ next (EOJ serves).
#
# EOJ is deployed over the PRIVATE network via SSH (its :22 need only allow OJ),
# so this box needs the EOJ key. Everything else (nginx toggles, OJ's own
# deploy) is local.
#
# Invocation (CD does this over SSH to OJ):
#   IMAGE=ghcr.io/sjh1108/oj-api:latest bash /opt/algoj/rolling-deploy.sh
#
# Env (defaults shown):
#   EOJ_HOST=172.31.32.237  EOJ_USER=ubuntu  EOJ_KEY=/opt/algoj/eoj.pem
#   EOJ_APP_DIR=/opt/algoj
#   OJ_UPSTREAM=127.0.0.1:8081  EOJ_UPSTREAM=172.31.32.237:8080
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
IMAGE="${IMAGE:-ghcr.io/sjh1108/oj-api:latest}"

EOJ_HOST="${EOJ_HOST:-172.31.32.237}"
EOJ_USER="${EOJ_USER:-ubuntu}"
EOJ_KEY="${EOJ_KEY:-/opt/algoj/eoj.pem}"
EOJ_APP_DIR="${EOJ_APP_DIR:-/opt/algoj}"

# Backend addresses for the nginx upstream (exported for render-upstream.sh).
export OJ_UPSTREAM="${OJ_UPSTREAM:-127.0.0.1:8081}"
export EOJ_UPSTREAM="${EOJ_UPSTREAM:-172.31.32.237:8080}"

log() { echo "[rolling] $*"; }
render() { bash "$DIR/nginx/render-upstream.sh" "$1"; }
ssh_eoj() {
  ssh -i "$EOJ_KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 \
    "${EOJ_USER}@${EOJ_HOST}" "$@"
}

# On ANY failure, un-drain both boxes. Each box's deploy-api-single.sh rolls
# itself back to its last healthy container on a bad boot, so restoring the
# upstream to "both active" leaves the site fully served. Then exit non-zero so
# CD marks the run failed.
on_err() { log "ERROR — restoring upstream (both active) and aborting"; render none || true; }
trap on_err ERR

# Push the latest single-box deploy script to EOJ.
log "syncing deploy-api-single.sh to EOJ"
scp -i "$EOJ_KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 \
  "$DIR/deploy-api-single.sh" "${EOJ_USER}@${EOJ_HOST}:${EOJ_APP_DIR}/deploy-api-single.sh"

# ── EOJ first — OJ keeps serving ──────────────────────────────
log "draining EOJ (OJ serves 100%)"
render eoj-down
log "deploying EOJ"
ssh_eoj "cd '${EOJ_APP_DIR}' && IMAGE='${IMAGE}' PORT=8080 PUBLISH_ADDR=0.0.0.0 bash deploy-api-single.sh"
log "restoring EOJ to pool"
render none

# ── OJ next — EOJ keeps serving ───────────────────────────────
log "draining OJ (EOJ serves 100%)"
render oj-down
log "deploying OJ"
IMAGE="$IMAGE" PORT=8081 PUBLISH_ADDR=127.0.0.1 bash "$DIR/deploy-api-single.sh"
log "restoring OJ to pool"
render none

trap - ERR
log "rolling deploy complete — both boxes updated, zero downtime."
