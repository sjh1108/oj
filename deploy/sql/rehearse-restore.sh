#!/usr/bin/env bash
# 복구 리허설 — 실제 AWS 덤프가 도착하기 전에 import 경로를 검증한다.
#
# 진짜 복구는 한 번뿐이고, 그때 문자셋이나 권한 문제로 막히면 RDS를 다시 켜서
# 덤프를 뜨는 비용이 또 든다. 그래서 지금 DB로 같은 절차를 미리 밟아본다:
#
#   운영 DB 덤프 → 임시 DB 생성 → import → 테이블·행 수 비교 → 임시 DB 삭제
#
# 운영 DB는 읽기만 한다. 임시 DB(<DB_NAME>_rehearsal)는 끝나면 반드시 지운다.
#
#   bash /opt/algoj/repo/deploy/sql/rehearse-restore.sh
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/algoj}"
ENV_FILE="${ENV_FILE:-$APP_DIR/.env}"
CONTAINER="${MYSQL_CONTAINER:-algoj-mysql}"

log() { echo "[rehearse] $*"; }
fail() { echo "[rehearse] FAIL: $*" >&2; exit 1; }

[ -f "$ENV_FILE" ] || fail "$ENV_FILE 가 없다"
env_value() { grep -m1 "^$1=" "$ENV_FILE" | cut -d= -f2- || true; }

DB_NAME="$(env_value DB_NAME)"
ROOT_PW="$(env_value MYSQL_ROOT_PASSWORD)"
[ -n "$DB_NAME" ] || fail "DB_NAME 이 .env 에 없다"
[ -n "$ROOT_PW" ] || fail "MYSQL_ROOT_PASSWORD 가 .env 에 없다"

SCRATCH="${DB_NAME}_rehearsal"
DUMP="$(mktemp /tmp/algoj-rehearsal-XXXXXX.sql)"

# 중간에 죽어도 임시 DB와 덤프 파일은 남기지 않는다.
cleanup() {
    rm -f "$DUMP"
    docker exec -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" \
        mysql -uroot -e "DROP DATABASE IF EXISTS \`$SCRATCH\`" >/dev/null 2>&1 || true
}
trap cleanup EXIT

mysql_q() {  # <db> <sql> → 헤더 없는 탭 구분 결과
    docker exec -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" \
        mysql -uroot -N -B -e "$2" "$1"
}

log "운영 DB($DB_NAME) 덤프 중 — 실제 복구에서 쓸 것과 같은 옵션"
docker exec -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" \
    mysqldump -uroot --single-transaction --routines \
    --default-character-set=utf8mb4 "$DB_NAME" > "$DUMP"
log "덤프 크기: $(du -h "$DUMP" | cut -f1)"

log "임시 DB($SCRATCH) 생성 후 import"
docker exec -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" mysql -uroot -e \
    "DROP DATABASE IF EXISTS \`$SCRATCH\`;
     CREATE DATABASE \`$SCRATCH\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
docker exec -i -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" \
    mysql -uroot --default-character-set=utf8mb4 "$SCRATCH" < "$DUMP"

log "테이블·행 수 비교"
mismatch=0
tables="$(mysql_q "$DB_NAME" 'SHOW TABLES')"
[ -n "$tables" ] || fail "운영 DB에 테이블이 없다 — 덤프할 것이 없다"

for t in $tables; do
    src="$(mysql_q "$DB_NAME" "SELECT COUNT(*) FROM \`$t\`")"
    dst="$(mysql_q "$SCRATCH" "SELECT COUNT(*) FROM \`$t\`" 2>/dev/null || echo "MISSING")"
    if [ "$src" = "$dst" ]; then
        printf '  OK    %-28s %s\n' "$t" "$src"
    else
        printf '  DIFF  %-28s 원본 %s / 복원 %s\n' "$t" "$src" "$dst"
        mismatch=1
    fi
done

# 스키마 버전이 함께 넘어와야 부팅 때 Flyway가 이어서 적용한다.
version="$(mysql_q "$SCRATCH" \
    'SELECT COALESCE(MAX(version), "(없음)") FROM flyway_schema_history' 2>/dev/null || echo "(테이블 없음)")"
log "복원된 스키마 버전: $version"

# 한글이 깨지지 않았는지 — 문자셋 사고는 행 수 비교로는 안 잡힌다.
charset="$(mysql_q "$SCRATCH" \
    "SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA
     WHERE SCHEMA_NAME = '$SCRATCH'")"
[ "$charset" = "utf8mb4" ] || { echo "  DIFF  문자셋이 $charset 이다 (utf8mb4 여야 함)"; mismatch=1; }

echo
[ "$mismatch" -eq 0 ] || fail "차이가 있다 — 위 DIFF 줄을 보고 실제 복구 전에 해결한다"
log "PASS — 덤프·import 절차가 이 박스에서 그대로 동작한다"
