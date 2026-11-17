#!/usr/bin/env bash
# [1회용] V9__shift_timestamps_to_kst.sql이 잘못 더한 9시간을 되돌린다.
#
# 무슨 일이 있었나
#   이전 API는 JVM이 UTC였지만, JDBC(serverTimezone=Asia/Seoul)가 저장할 때 KST로 바꿔 넣어서
#   DB에는 원래 한국 시각이 맞게 들어 있었다. 읽을 때 다시 UTC로 바뀌어 화면만 9시간 일렀다.
#   PR #87이 JVM을 Asia/Seoul로 고정해 표시 문제는 해결했지만, 함께 넣은 V9가 이미 맞던
#   DB 값에 +9시간을 더해 버렸다 (운영 V9 실행: 2026-11-18 00:40:34 KST).
#
# 무엇을 되돌리나 (모두 -9시간)
#   - submissions: id <= 440 (배포 전 마지막 제출, 운영 데이터로 확인)
#   - users / problems / user_preferences / problem_notes 의 created_at·updated_at 중
#       V9 실행 시각보다 이전 값  → V9가 옮긴 값이 확실하다
#       지금보다 미래인 값        → V9가 옮긴 값이 확실하다
#     그 사이 [V9 실행 시각, 지금] 값은 배포 후 새로 찍힌 값일 수도 있어 건드리지 않고 출력만 한다.
#
# 박스에서 1회 실행:
#   bash deploy/sql/2026-11-undo-v9-kst-shift.sh
# 두 번 돌려도 안전하다 — submissions 440번이 이미 되돌려져 있으면 아무것도 하지 않는다.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/algoj}"
CONTAINER="${MYSQL_CONTAINER:-algoj-mysql}"
BACKUP_DIR="${BACKUP_DIR:-$APP_DIR/backups}"
V9_AT='2026-11-18 00:40:34'
LAST_OLD_SUBMISSION=440

log() { echo "[undo-v9] $*"; }
# 비밀번호·DB 이름은 MySQL 컨테이너 환경변수(.env에서 온 값)를 그대로 쓴다.
# 비밀번호 경고만 숨기고 에러는 그대로 보이게, mysql이 실패하면 스크립트도 멈춘다.
sql() { docker exec -i "$CONTAINER" sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" "$@" 2>/tmp/mysql.err; rc=$?; grep -v "Using a password" /tmp/mysql.err >&2; exit $rc' sh "$@"; }

# 0. 이미 되돌렸는지 확인 — 440번 제출은 V9 이후 2026-11-18 01:20:30, 되돌리면 그 전날 16:20:30.
cur="$(echo "SELECT created_at FROM submissions WHERE id = $LAST_OLD_SUBMISSION" | sql -N)"
log "submissions #$LAST_OLD_SUBMISSION created_at = $cur"
if [[ -z "$cur" ]]; then log "ERROR: #$LAST_OLD_SUBMISSION 제출이 없다 — 중단"; exit 1; fi
if [[ "$cur" < "$V9_AT" ]]; then log "이미 되돌려져 있다 — 아무것도 하지 않는다."; exit 0; fi

# 1. 백업 (해당 테이블만)
mkdir -p "$BACKUP_DIR"
backup="$BACKUP_DIR/before-undo-v9-$(date +%Y%m%dT%H%M%S).sql.gz"
docker exec "$CONTAINER" sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction "$MYSQL_DATABASE" users problems submissions user_preferences problem_notes 2>/dev/null' | gzip > "$backup"
if [[ "$(gzip -dc "$backup" | tail -1)" != *"Dump completed"* ]]; then
  log "ERROR: 백업이 끝까지 써지지 않았다 ($backup) — 중단"; exit 1
fi
log "백업: $backup"

# 2. 되돌리기 (한 트랜잭션)
sql <<SQL
SET @v9 = TIMESTAMP('$V9_AT');
SET @now = UTC_TIMESTAMP(6) + INTERVAL 9 HOUR;  -- 한국 시각 (MySQL 시간대와 무관)
START TRANSACTION;
UPDATE submissions SET created_at = created_at - INTERVAL 9 HOUR WHERE id <= $LAST_OLD_SUBMISSION;
UPDATE users SET
  created_at = IF(created_at < @v9 OR created_at > @now, created_at - INTERVAL 9 HOUR, created_at),
  updated_at = IF(updated_at < @v9 OR updated_at > @now, updated_at - INTERVAL 9 HOUR, updated_at);
UPDATE problems SET
  created_at = IF(created_at < @v9 OR created_at > @now, created_at - INTERVAL 9 HOUR, created_at),
  updated_at = IF(updated_at < @v9 OR updated_at > @now, updated_at - INTERVAL 9 HOUR, updated_at);
UPDATE user_preferences SET
  created_at = IF(created_at < @v9 OR created_at > @now, created_at - INTERVAL 9 HOUR, created_at),
  updated_at = IF(updated_at < @v9 OR updated_at > @now, updated_at - INTERVAL 9 HOUR, updated_at);
UPDATE problem_notes SET
  created_at = IF(created_at < @v9 OR created_at > @now, created_at - INTERVAL 9 HOUR, created_at),
  updated_at = IF(updated_at < @v9 OR updated_at > @now, updated_at - INTERVAL 9 HOUR, updated_at);
COMMIT;
SQL
log "되돌리기 완료."

# 3. 결과 확인
log "최근 제출:"
echo "SELECT id, created_at FROM submissions ORDER BY id DESC LIMIT 5" | sql -t
log "판단 보류(그대로 둔) 행 — 비어 있으면 끝:"
sql -t <<SQL
SET @v9 = TIMESTAMP('$V9_AT');
SET @now = UTC_TIMESTAMP(6) + INTERVAL 9 HOUR;
SELECT 'users' AS t, id, created_at, updated_at FROM users WHERE created_at BETWEEN @v9 AND @now OR updated_at BETWEEN @v9 AND @now
UNION ALL SELECT 'problems', id, created_at, updated_at FROM problems WHERE created_at BETWEEN @v9 AND @now OR updated_at BETWEEN @v9 AND @now
UNION ALL SELECT 'user_preferences', user_id, created_at, updated_at FROM user_preferences WHERE created_at BETWEEN @v9 AND @now OR updated_at BETWEEN @v9 AND @now
UNION ALL SELECT 'problem_notes', id, created_at, updated_at FROM problem_notes WHERE created_at BETWEEN @v9 AND @now OR updated_at BETWEEN @v9 AND @now;
SQL
