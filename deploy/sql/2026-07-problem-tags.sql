-- [적용 완료 / superseded] problem_tags 수동 생성 스크립트.
-- Flyway 도입 전에 프로드에 1회 수동 적용했다. 이 테이블은 이제
-- db/migration/V1__baseline.sql 에 포함되어 있으므로 이 파일을 다시 실행할
-- 일은 없다 — 기록용으로만 남겨둔다.

CREATE TABLE IF NOT EXISTS problem_tags (
    problem_id BIGINT      NOT NULL,
    tag        VARCHAR(30) NOT NULL,
    PRIMARY KEY (problem_id, tag),
    CONSTRAINT fk_problem_tags_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
