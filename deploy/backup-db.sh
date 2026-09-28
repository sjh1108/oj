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
# 4. backup.env 에 업로드 대상이 있으면 박스 밖으로 올린다
#    - BACKUP_RCLONE_REMOTE: rclone 으로 Google Drive 등 (오래된 원격 백업도 정리)
#      지문 이미지($IMAGE_DIR)도 <원격>/images 로 함께 복사한다 — DB 덤프에는 URL만 있다.
#    - BACKUP_S3_BUCKET:     S3 호환 스토리지
# 5. backup.env 에 BACKUP_ALERT_WEBHOOK(디스코드 웹훅)이 있으면 실패를 알린다.
#    실패한 뒤 다시 성공하면 복구 알림도 한 번 보낸다.
#
#   bash /opt/algoj/repo/deploy/backup-db.sh --check
#
# 백업을 뜨지 않고 마지막 성공 시각만 본다. BACKUP_ALERT_STALE_HOURS(기본 48)시간 넘게
# 성공 기록이 없으면 알린다 — cron 줄이 사라졌거나 스크립트가 아예 안 도는 경우는
# 실패 알림으로는 잡히지 않아서 따로 둔다. 별도 cron 줄로 돌린다 (backup.md 4절).
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
# 지문 이미지 — deploy-api.sh 가 API 컨테이너에 마운트하는 디렉터리.
IMAGE_DIR="${IMAGE_DIR:-$APP_DIR/images}"

# 마지막 성공 시각(--check 가 본다)과, 실패 알림을 보낸 뒤 아직 복구되지 않았다는 표시.
SUCCESS_STAMP="$BACKUP_DIR/.last-success"
ALERT_OPEN="$BACKUP_DIR/.alert-open"

# 이번 실행에서 나온 실패 이유 — 알림에 담는다.
FAILS=()
log() {
    echo "[backup $(date '+%F %T')] $*"
    case "$*" in FAIL:*) FAILS+=("${*#FAIL: }") ;; esac
}
fail() {
    echo "[backup $(date '+%F %T')] FAIL: $*" >&2
    FAILS+=("$*")
    exit 1
}

env_value() { grep -m1 "^$1=" "$ENV_FILE" | cut -d= -f2- || true; }
backup_value() {
    [ -f "$BACKUP_ENV_FILE" ] || return 0
    grep -m1 "^$1=" "$BACKUP_ENV_FILE" | cut -d= -f2- || true
}

# ─── 디스코드 알림 ──────────────────────────────────────────────
# 알림이 실패해도 백업 결과(종료 코드)는 바뀌지 않는다 — 로그에 한 줄 남기고 넘어간다.
json_escape() {
    local s="$1"
    s="${s//\\/\\\\}"
    s="${s//\"/\\\"}"
    s="${s//$'\n'/\\n}"
    s="${s//$'\r'/}"
    s="${s//$'\t'/ }"
    printf '%s' "$s"
}
notify() {
    local url msg
    url="$(backup_value BACKUP_ALERT_WEBHOOK)"
    [ -n "$url" ] || return 0
    msg="$1"
    # 디스코드 메시지는 2000자까지다.
    [ "${#msg}" -le 1900 ] || msg="${msg:0:1900}…"
    # 웹훅 URL 자체가 비밀번호라 명령줄(ps)에 쓰지 않고 stdin 설정으로 넘긴다.
    printf 'url = "%s"\n' "$url" \
        | curl -K - -m 10 -fsS -o /dev/null -X POST \
            -H 'Content-Type: application/json' \
            --data-binary "{\"content\":\"$(json_escape "$msg")\"}" \
        || echo "[backup $(date '+%F %T')] 경고: 디스코드 알림 전송 실패" >&2
}

# ─── --check: 마지막 성공이 너무 오래됐는지만 본다 ─────────────────
if [ "${1:-}" = "--check" ]; then
    stale_hours="$(backup_value BACKUP_ALERT_STALE_HOURS)"
    stale_hours="${stale_hours:-48}"
    last=""
    if [ -f "$SUCCESS_STAMP" ]; then
        last="$(stat -c %Y "$SUCCESS_STAMP")"
    else
        # 이 기능이 들어오기 전 백업 — 가장 최근 로컬 덤프로 대신한다.
        newest="$(find "$BACKUP_DIR" -maxdepth 1 -name 'algoj-*.sql.gz' 2>/dev/null | sort | tail -n 1 || true)"
        [ -n "$newest" ] && last="$(stat -c %Y "$newest")"
    fi
    if [ -z "$last" ]; then
        log "성공한 백업 기록이 없다"
        notify "⚠️ **[algoj] DB 백업 기록 없음** ($(hostname))"$'\n'"성공한 백업이 한 번도 없다. cron 등록과 \`/opt/algoj/backup.log\`를 확인한다."
        exit 1
    fi
    age_hours=$(( ($(date +%s) - last) / 3600 ))
    last_text="$(date -d "@$last" '+%F %T')"
    if [ "$age_hours" -ge "$stale_hours" ]; then
        log "마지막 성공 ${last_text} (${age_hours}시간 전) — 기준 ${stale_hours}시간 초과"
        notify "⚠️ **[algoj] DB 백업이 ${age_hours}시간째 성공하지 않았다** ($(hostname))"$'\n'"마지막 성공: ${last_text}. cron이 돌고 있는지와 \`/opt/algoj/backup.log\`를 확인한다."
        exit 1
    fi
    log "마지막 성공 ${last_text} (${age_hours}시간 전) — 정상"
    exit 0
fi

# ─── 실패 알림 ──────────────────────────────────────────────────
# fail() 뿐 아니라 set -e 로 예상 못 한 곳에서 죽어도 여기로 온다.
part=""
on_exit() {
    local rc=$?
    [ -z "$part" ] || rm -f "$part"
    [ "$rc" -ne 0 ] || return 0
    local reasons=""
    if [ "${#FAILS[@]}" -eq 0 ]; then
        reasons="- 예상하지 못한 오류로 중단됐다 (종료 코드 $rc)"
    else
        local r
        for r in "${FAILS[@]}"; do reasons+="- $r"$'\n'; done
    fi
    mkdir -p "$BACKUP_DIR" 2>/dev/null && touch "$ALERT_OPEN" 2>/dev/null || true
    notify "🚨 **[algoj] DB 백업 실패** ($(hostname), $(date '+%F %T'))"$'\n'"${reasons%$'\n'}"$'\n'"자세한 내용은 \`/opt/algoj/backup.log\`."
}
trap on_exit EXIT

# 성공으로 끝날 때 — 마지막 성공 시각을 남기고, 직전에 실패를 알렸다면 복구됐다고 알린다.
succeed() {
    touch "$SUCCESS_STAMP"
    if [ -f "$ALERT_OPEN" ]; then
        rm -f "$ALERT_OPEN"
        notify "✅ **[algoj] DB 백업 복구** ($(hostname)) — $1"
    fi
    log "완료"
}

[ -f "$ENV_FILE" ] || fail "$ENV_FILE 가 없다"

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
part=""
chmod 600 "$final"
log "로컬 저장: $final ($(du -h "$final" | cut -f1))"

# 이름에 시각이 들어 있어 정렬 순서 = 시간 순서다.
mapfile -t old < <(find "$BACKUP_DIR" -maxdepth 1 -name 'algoj-*.sql.gz' | sort | head -n "-$BACKUP_KEEP")
for f in "${old[@]}"; do
    rm -f "$f"
    log "오래된 백업 삭제: $(basename "$f")"
done

# ─── 박스 밖 업로드 ─────────────────────────────────────────────
# 두 방식 모두 박스에 도구를 깔지 않고 공식 도커 이미지로 한 번 돌린다 (arm64 지원).
# 둘 다 설정돼 있으면 둘 다 올린다. 하나가 실패해도 다른 쪽은 시도한다.

# rclone — Google Drive·OneDrive 등. 로그인 정보는 $RCLONE_DIR/rclone.conf (backup.md 2절).
upload_rclone() {
    local remote="$1"
    local keep_days rclone_dir
    keep_days="$(backup_value BACKUP_REMOTE_KEEP_DAYS)"
    keep_days="${keep_days:-30}"
    rclone_dir="${RCLONE_DIR:-$APP_DIR/rclone}"
    [ -f "$rclone_dir/rclone.conf" ] || { log "FAIL: $rclone_dir/rclone.conf 가 없다"; return 1; }

    # ubuntu 사용자로 돌린다 — rclone 은 토큰을 갱신할 때 설정 파일을 다시 쓰는데,
    # root 로 돌면 그 파일이 root 소유가 돼 다음부터 손으로 고칠 수 없다.
    local image_mount=()
    [ -d "$IMAGE_DIR" ] && image_mount=(-v "$IMAGE_DIR:/images:ro")
    rclone() {
        docker run --rm --user "$(id -u):$(id -g)" \
            -v "$rclone_dir:/config/rclone" \
            -v "$BACKUP_DIR:/backups:ro" \
            "${image_mount[@]}" \
            rclone/rclone --config /config/rclone/rclone.conf "$@"
    }

    log "업로드: $remote/$name"
    rclone copyto "/backups/$name" "$remote/$name" || { log "FAIL: rclone 업로드 실패"; return 1; }

    # 이미지는 이름(UUID)이 바뀌지 않으니 copy 로 새 파일만 올린다. 지우지 않으므로 sync 가 아니다.
    if [ "${#image_mount[@]}" -gt 0 ]; then
        log "지문 이미지 복사: $IMAGE_DIR → $remote/images"
        rclone copy /images "$remote/images" --exclude '.upload-*' \
            || { log "FAIL: 이미지 업로드 실패"; return 1; }
    fi

    # 드라이브에는 S3 같은 자동 삭제 규칙이 없어서 여기서 직접 지운다.
    # 휴지통으로 보내면 용량을 계속 차지하므로 바로 지운다 (Google Drive 기준 옵션).
    log "원격에서 ${keep_days}일 지난 백업 정리"
    rclone delete "$remote" --min-age "${keep_days}d" --include 'algoj-*.sql.gz' \
        --drive-use-trash=false \
        || log "경고: 원격 정리 실패 — 업로드는 됐다"
}

# S3 호환 스토리지 — AWS S3·OCI Object Storage·Cloudflare R2. 오래된 객체는 버킷 수명 주기 규칙으로 지운다.
upload_s3() {
    local bucket="$1"
    local endpoint region prefix key_id secret
    endpoint="$(backup_value BACKUP_S3_ENDPOINT)"
    region="$(backup_value BACKUP_S3_REGION)"
    prefix="$(backup_value BACKUP_S3_PREFIX)"
    prefix="${prefix:-db}"
    key_id="$(backup_value BACKUP_S3_ACCESS_KEY_ID)"
    secret="$(backup_value BACKUP_S3_SECRET_ACCESS_KEY)"
    if [ -z "$key_id" ] || [ -z "$secret" ]; then
        log "FAIL: BACKUP_S3_ACCESS_KEY_ID / BACKUP_S3_SECRET_ACCESS_KEY 가 없다"
        return 1
    fi

    local endpoint_args=()
    [ -n "$endpoint" ] && endpoint_args=(--endpoint-url "$endpoint")

    # 키는 값 없이 이름만 넘긴다 — 명령줄에 값을 쓰면 `ps`로 다른 사용자에게 보인다.
    export AWS_ACCESS_KEY_ID="$key_id" AWS_SECRET_ACCESS_KEY="$secret"
    export AWS_DEFAULT_REGION="${region:-us-east-1}"
    # aws CLI 최신판은 업로드에 새 체크섬 헤더를 붙이는데, S3 호환 스토리지(OCI·R2 등)는
    # 이를 거부할 수 있다. 필요할 때만 계산하게 해 둔다.
    export AWS_REQUEST_CHECKSUM_CALCULATION=when_required
    export AWS_RESPONSE_CHECKSUM_VALIDATION=when_required

    log "업로드: s3://$bucket/$prefix/$name"
    docker run --rm \
        -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION \
        -e AWS_REQUEST_CHECKSUM_CALCULATION -e AWS_RESPONSE_CHECKSUM_VALIDATION \
        -v "$BACKUP_DIR:/backups:ro" \
        amazon/aws-cli "${endpoint_args[@]}" \
        s3 cp "/backups/$name" "s3://$bucket/$prefix/$name" --only-show-errors \
        || { log "FAIL: S3 업로드 실패"; return 1; }
}

RCLONE_REMOTE="$(backup_value BACKUP_RCLONE_REMOTE)"
S3_BUCKET="$(backup_value BACKUP_S3_BUCKET)"
if [ -z "$RCLONE_REMOTE" ] && [ -z "$S3_BUCKET" ]; then
    log "$BACKUP_ENV_FILE 에 업로드 대상이 없다 — 박스 밖 업로드는 건너뛴다"
    succeed "로컬 저장만 ($name)"
    exit 0
fi

upload_failed=0
if [ -n "$RCLONE_REMOTE" ]; then upload_rclone "$RCLONE_REMOTE" || upload_failed=1; fi
if [ -n "$S3_BUCKET" ]; then upload_s3 "$S3_BUCKET" || upload_failed=1; fi
[ "$upload_failed" -eq 0 ] || fail "업로드 실패 — 로컬 백업($final)은 남아 있다"

succeed "$name 저장·업로드"
