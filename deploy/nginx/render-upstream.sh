#!/usr/bin/env bash
# Manage the two-box nginx upstream on the OJ box (OJ + EOJ redundancy).
#
# Writes the `algoj_api` upstream listing BOTH API backends and optionally marks
# one 'down' so a rolling deploy can drain that box (all traffic goes to the
# other) while its container is replaced. Then validates and reloads nginx.
#
# Usage (on OJ):
#   render-upstream.sh none        # both backends active — steady state
#   render-upstream.sh oj-down     # drain OJ  (EOJ serves 100%) → deploy OJ
#   render-upstream.sh eoj-down    # drain EOJ (OJ serves 100%) → deploy EOJ
#
# Backend addresses come from env (defaults below). OJ listens on loopback:8081
# (nginx-internal already owns 127.0.0.1:8080); EOJ is reached over the private
# network (its security group restricts :8080 to OJ).
#   OJ_UPSTREAM=127.0.0.1:8081
#   EOJ_UPSTREAM=172.31.32.237:8080
#
# Only ever marks ONE side down — never both (nginx refuses an all-down upstream).
set -euo pipefail

TARGET="${1:-none}"
OJ_UPSTREAM="${OJ_UPSTREAM:-127.0.0.1:8081}"
EOJ_UPSTREAM="${EOJ_UPSTREAM:-172.31.32.237:8080}"
UPSTREAM_CONF="${UPSTREAM_CONF:-/etc/nginx/conf.d/algoj-upstream.conf}"

oj_down=""; eoj_down=""
case "$TARGET" in
  none)     ;;
  oj-down)  oj_down=" down" ;;
  eoj-down) eoj_down=" down" ;;
  *) echo "usage: $0 {none|oj-down|eoj-down}" >&2; exit 2 ;;
esac

tmp="$(mktemp)"
cat > "$tmp" <<EOF
# Managed by deploy/nginx/render-upstream.sh — do not hand-edit.
# Two-box redundancy: OJ (local) + EOJ (private network). least_conn spreads
# traffic; max_fails ejects a box that dies. Rolling deploys mark one 'down'.
upstream algoj_api {
    least_conn;
    server ${OJ_UPSTREAM}${oj_down} max_fails=2 fail_timeout=10s;   # OJ
    server ${EOJ_UPSTREAM}${eoj_down} max_fails=2 fail_timeout=10s;   # EOJ
}
EOF

sudo cp "$tmp" "$UPSTREAM_CONF"
rm -f "$tmp"
sudo nginx -t
sudo nginx -s reload
echo "[render-upstream] target=$TARGET → OJ=${OJ_UPSTREAM}${oj_down:+(down)} EOJ=${EOJ_UPSTREAM}${eoj_down:+(down)}"
