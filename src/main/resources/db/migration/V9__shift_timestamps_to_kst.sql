-- 지금까지 운영 API 컨테이너에는 TZ가 없어 JVM이 UTC로 돌았고, LocalDateTime.now()로
-- 찍은 시각(제출 시각 등)이 UTC 벽시계 시각으로 저장돼 있었다. 이번 버전부터 JVM 기본
-- 시간대를 Asia/Seoul로 고정하므로(OjApplication), 기존 행도 +9시간 옮겨 한국 시각으로 맞춘다.
--
-- 이동량은 Flyway 플레이스홀더로 받는다: prod 프로필만 9, 그 외(dev·로컬·CI)는 0.
-- 로컬 개발 PC는 이미 한국 시간대로 기록해 왔으므로 옮기면 안 된다.
UPDATE users            SET created_at = created_at + INTERVAL ${kst_shift_hours} HOUR,
                            updated_at = updated_at + INTERVAL ${kst_shift_hours} HOUR;
UPDATE problems         SET created_at = created_at + INTERVAL ${kst_shift_hours} HOUR,
                            updated_at = updated_at + INTERVAL ${kst_shift_hours} HOUR;
UPDATE submissions      SET created_at = created_at + INTERVAL ${kst_shift_hours} HOUR;
UPDATE user_preferences SET created_at = created_at + INTERVAL ${kst_shift_hours} HOUR,
                            updated_at = updated_at + INTERVAL ${kst_shift_hours} HOUR;
UPDATE problem_notes    SET created_at = created_at + INTERVAL ${kst_shift_hours} HOUR,
                            updated_at = updated_at + INTERVAL ${kst_shift_hours} HOUR;
