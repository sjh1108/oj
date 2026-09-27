-- 선정 문제 알림 기록. 관리자가 일괄 업로드 화면에서 디스코드로 보낸 공지를 남긴다.
--
-- 주차는 달력이 아니라 "그 달에 실제로 진행한 스터디가 몇 번째인가"로 센다. 쉬는 주에는
-- 공지를 보내지 않으므로, 같은 달에 이미 기록된 스터디 날짜 수 + 1이 곧 주차가 된다.
-- 그래서 공지를 보낸 날이 아니라 스터디 날짜(study_date)를 기준으로 기록한다.
CREATE TABLE problem_announcements
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    study_date     DATE        NOT NULL,
    week_of_month  INT         NOT NULL,
    content        TEXT        NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_problem_announcements_study_date (study_date)
) ENGINE = InnoDB;
