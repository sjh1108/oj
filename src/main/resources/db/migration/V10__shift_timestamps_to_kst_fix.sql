-- V9를 다시 하는 보정. V9는 이동량을 prod 프로필 설정(kst_shift_hours)에 맡겼는데,
-- 운영 박스가 prod 프로필로 뜨지 않아 0시간으로 실행됐고 기존 UTC 시각이 그대로 남았다.
-- 이번에는 프로필에 기대지 않고 "배포 시점 이전 값만" 옮긴다.
--
-- 기준 시각: KST 고정 버전(PR #87)이 운영에 올라간 2026-11-17 15:40 UTC.
--  - 그 전에 찍힌 값은 UTC 벽시계 → 모두 '2026-11-17 15:41:00'보다 작다.
--  - 그 뒤에 찍힌 값은 KST 벽시계 → 모두 '2026-11-18 00:40:00' 이상이다.
-- 두 구간이 9시간 떨어져 있어 컬럼별로 기준 시각 미만만 옮기면 섞이지 않는다
-- (생성은 배포 전·수정은 배포 후인 행도 created_at만 옮겨진다).
--
-- 안전장치: 만약 V9가 실제로 9시간을 옮겼다면, 배포 직전 9시간 동안의 값이
-- [15:41, 다음날 00:40) 구간에 있을 것이다. 그 구간에 값이 하나라도 있으면 이미
-- 보정된 것으로 보고 아무것도 옮기지 않는다 (이중 보정 방지).
SET @cutoff = TIMESTAMP('2026-11-17 15:41:00');
SET @kst_start = TIMESTAMP('2026-11-18 00:40:00');

SET @already_shifted = (
    SELECT COUNT(*) FROM (
        SELECT created_at AS t FROM submissions
        UNION ALL SELECT created_at FROM users
        UNION ALL SELECT updated_at FROM users
        UNION ALL SELECT created_at FROM problems
        UNION ALL SELECT updated_at FROM problems
        UNION ALL SELECT created_at FROM user_preferences
        UNION ALL SELECT updated_at FROM user_preferences
        UNION ALL SELECT created_at FROM problem_notes
        UNION ALL SELECT updated_at FROM problem_notes
    ) x
    WHERE t >= @cutoff AND t < @kst_start
) > 0;

SET @h = IF(@already_shifted, 0, 9);

UPDATE users SET
    created_at = IF(created_at < @cutoff, created_at + INTERVAL @h HOUR, created_at),
    updated_at = IF(updated_at < @cutoff, updated_at + INTERVAL @h HOUR, updated_at);
UPDATE problems SET
    created_at = IF(created_at < @cutoff, created_at + INTERVAL @h HOUR, created_at),
    updated_at = IF(updated_at < @cutoff, updated_at + INTERVAL @h HOUR, updated_at);
UPDATE submissions SET
    created_at = IF(created_at < @cutoff, created_at + INTERVAL @h HOUR, created_at);
UPDATE user_preferences SET
    created_at = IF(created_at < @cutoff, created_at + INTERVAL @h HOUR, created_at),
    updated_at = IF(updated_at < @cutoff, updated_at + INTERVAL @h HOUR, updated_at);
UPDATE problem_notes SET
    created_at = IF(created_at < @cutoff, created_at + INTERVAL @h HOUR, created_at),
    updated_at = IF(updated_at < @cutoff, updated_at + INTERVAL @h HOUR, updated_at);
