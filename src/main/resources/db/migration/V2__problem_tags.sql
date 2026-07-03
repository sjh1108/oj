-- Problem tags (free-form classification, element collection on problems).
-- IF NOT EXISTS because prod got this table manually before Flyway existed —
-- there it's a no-op; databases created before the tag feature get it here.

CREATE TABLE IF NOT EXISTS problem_tags
(
    problem_id BIGINT      NOT NULL,
    tag        VARCHAR(30) NOT NULL,
    PRIMARY KEY (problem_id, tag),
    CONSTRAINT fk_problem_tags_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id)
) ENGINE = InnoDB;
