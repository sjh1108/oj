-- 선정 문제 알림을 스터디 날짜 대신 "몇 년 몇 월 몇 주차"로 기록한다.
-- 관리자가 날짜를 고르는 대신 월·주차를 드롭다운으로 고르기로 해서, 날짜는 더 이상 없다.
-- 기존 행은 스터디 날짜의 연·월로 옮기고 주차는 그대로 둔다.
ALTER TABLE problem_announcements
    ADD COLUMN announce_year  INT NULL,
    ADD COLUMN announce_month INT NULL;

UPDATE problem_announcements
SET announce_year  = YEAR(study_date),
    announce_month = MONTH(study_date);

ALTER TABLE problem_announcements
    MODIFY announce_year INT NOT NULL,
    MODIFY announce_month INT NOT NULL,
    DROP INDEX idx_problem_announcements_study_date,
    DROP COLUMN study_date,
    ADD INDEX idx_problem_announcements_year_month (announce_year, announce_month);
