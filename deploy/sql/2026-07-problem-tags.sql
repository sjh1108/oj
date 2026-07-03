-- Problem tags (prod runs ddl-auto=validate, so apply manually BEFORE deploying
-- the image that contains the tag feature):
--
--   docker exec -i algoj-mysql mysql -ualgoj -p"$(grep '^DB_PASSWORD=' /opt/algoj/.env | cut -d= -f2)" algoj \
--     < 2026-07-problem-tags.sql

CREATE TABLE IF NOT EXISTS problem_tags (
    problem_id BIGINT      NOT NULL,
    tag        VARCHAR(30) NOT NULL,
    PRIMARY KEY (problem_id, tag),
    CONSTRAINT fk_problem_tags_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
