#!/usr/bin/env bash
# DB 백업 — 운영 MySQL을 덤프해 박스에 보관하고, 설정돼 있으면 박스 밖으로도 올린다.
#
# AWS 시절에는 RDS가 자동 백업을 해 줬지만, 지금 MySQL은 박스 안 컨테이너라 아무도
# 백업하지 않는다. cron으로 매일 돌린다 (설치는 deploy/backup.md).
#
#   bash /opt/algoj/repo/deploy/backup-db.sh
#
# 1. algoj-mysql 안에서 mysqldump → gzip → $BACKUP_DIR/algoj-<시각>.sql.gz
# 2. 덤프가 끝까지 써졌는지 검사 (gzip 무결성 + mysqldump 완료 표시)
# 3. 최근 BACKUP_KEEP 개만 남기고 오래된 로컬 백업 삭제
# 4. backup.env 에 BACKUP_S3_BUCKET 이 있으면 S3 호환 스토리지로 업로드
#
# 로컬 백업만으로는 박스가 통째로 사라지는 경우를 못 막는다 — 4번을 켜 두는 게 목표다.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/algoj}"
ENV_FILE="${ENV_FILE:-$APP_DIR/.env}"
# 업로드용 키는 .env 와 따로 둔다 — .env 는 deploy-api.sh 가 API 컨테이너에 통째로 넣는다.
BACKUP_ENV_FILE="${BACKUP_ENV_FILE:-$APP_DIR/backup.env}"
CONTAINER="${MYSQL_CONTAINER:-algoj-mysql}"
BACKUP_DIR="${BACKUP_DIR:-$APP_DIR/backups}"
BACKUP_KEEP="${BACKUP_KEEP:-7}"
# 정상 덤프는 수백 MB다. 이보다 작으면 무언가 잘못된 것이다(빈 DB, 권한 오류 등).
MIN_BYTES="${BACKUP_MIN_BYTES:-1048576}"

log() { echo "[backup $(date '+%F %T')] $*"; }
fail() { echo "[backup $(date '+%F %T')] FAIL: $*" >&2; exit 1; }

[ -f "$ENV_FILE" ] || fail "$ENV_FILE 가 없다"
env_value() { grep -m1 "^$1=" "$ENV_FILE" | cut -d= -f2- || true; }
backup_value() {
    [ -f "$BACKUP_ENV_FILE" ] || return 0
    grep -m1 "^$1=" "$BACKUP_ENV_FILE" | cut -d= -f2- || true
}

DB_NAME="$(env_value DB_NAME)"
ROOT_PW="$(env_value MYSQL_ROOT_PASSWORD)"
[ -n "$DB_NAME" ] || fail "DB_NAME 이 .env 에 없다"
[ -n "$ROOT_PW" ] || fail "MYSQL_ROOT_PASSWORD 가 .env 에 없다"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"   # 덤프에는 계정 정보가 들어 있다

name="algoj-$(date '+%Y-%m-%dT%H%M').sql.gz"
final="$BACKUP_DIR/$name"
# 다 쓰기 전에는 .part 로 둔다 — 중간에 죽은 파일이 정상 백업처럼 보이지 않게.
part="$final.part"
trap 'rm -f "$part"' EXIT

log "덤프 시작 ($DB_NAME → $name)"
# --single-transaction: 테이블을 잠그지 않고 일관된 시점으로 뜬다 (InnoDB).
# --set-gtid-purged=OFF: 다른 서버로 import 할 때 GTID 문장 때문에 막히지 않게.
export MYSQL_PWD="$ROOT_PW"   # 이름만 넘겨 명령줄(ps)에 비밀번호가 드러나지 않게
docker exec -e MYSQL_PWD "$CONTAINER" \
    mysqldump -uroot --single-transaction --routines --set-gtid-purged=OFF \
    --default-character-set=utf8mb4 "$DB_NAME" \
    | gzip -6 > "$part" \
    || fail "mysqldump 실패 — 위 오류를 본다"

gzip -t "$part" || fail "gzip 무결성 검사 실패"
size="$(stat -c %s "$part")"
[ "$size" -ge "$MIN_BYTES" ] || fail "덤프가 너무 작다 (${size} bytes)"
# mysqldump 는 정상 종료할 때만 마지막 줄에 이 표시를 남긴다.
last_line="$(zcat "$part" | tail -n 1)"
case "$last_line" in
    "-- Dump completed"*) ;;
    *) fail "덤프가 끝까지 써지지 않았다 (완료 표시 없음)" ;;
esac

mv "$part" "$final"
chmod 600 "$final"
log "로컬 저장: $final ($(du -h "$final" | cut -f1))"

# 이름에 시각이 들어 있어 정렬 순서 = 시간 순서다.
mapfile -t old < <(find "$BACKUP_DIR" -maxdepth 1 -name 'algoj-*.sql.gz' | sort | head -n "-$BACKUP_KEEP")
for f in "${old[@]}"; do
    rm -f "$f"
    log "오래된 백업 삭제: $(basename "$f")"
done

BUCKET="$(backup_value BACKUP_S3_BUCKET)"
if [ -z "$BUCKET" ]; then
    log "$BACKUP_ENV_FILE 에 BACKUP_S3_BUCKET 이 없다 — 박스 밖 업로드는 건너뛴다"
    log "완료"
    exit 0
fi

ENDPOINT="$(backup_value BACKUP_S3_ENDPOINT)"
REGION="$(backup_value BACKUP_S3_REGION)"
PREFIX="$(backup_value BACKUP_S3_PREFIX)"
PREFIX="${PREFIX:-db}"
KEY_ID="$(backup_value BACKUP_S3_ACCESS_KEY_ID)"
SECRET="$(backup_value BACKUP_S3_SECRET_ACCESS_KEY)"
[ -n "$KEY_ID" ] && [ -n "$SECRET" ] || fail "BACKUP_S3_ACCESS_KEY_ID / BACKUP_S3_SECRET_ACCESS_KEY 가 없다"

# 박스에 aws CLI 를 깔지 않고 공식 이미지로 한 번 돌린다 (arm64 지원).
endpoint_args=()
[ -n "$ENDPOINT" ] && endpoint_args=(--endpoint-url "$ENDPOINT")

# 키는 값 없이 이름만 넘긴다 — 명령줄에 값을 쓰면 `ps`로 다른 사용자에게 보인다.
export AWS_ACCESS_KEY_ID="$KEY_ID" AWS_SECRET_ACCESS_KEY="$SECRET"
export AWS_DEFAULT_REGION="${REGION:-us-east-1}"
# aws CLI 최신판은 업로드에 새 체크섬 헤더를 붙이는데, S3 호환 스토리지(OCI·R2 등)는
# 이를 거부할 수 있다. 필요할 때만 계산하게 해 둔다.
export AWS_REQUEST_CHECKSUM_CALCULATION=when_required
export AWS_RESPONSE_CHECKSUM_VALIDATION=when_required

log "업로드: s3://$BUCKET/$PREFIX/$name"
docker run --rm \
    -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION \
    -e AWS_REQUEST_CHECKSUM_CALCULATION -e AWS_RESPONSE_CHECKSUM_VALIDATION \
    -v "$BACKUP_DIR:/backups:ro" \
    amazon/aws-cli "${endpoint_args[@]}" \
    s3 cp "/backups/$name" "s3://$BUCKET/$PREFIX/$name" --only-show-errors \
    || fail "업로드 실패 — 로컬 백업($final)은 남아 있다"

log "완료"
