-- PyPy 3 as a selectable submission language. Values stay alphabetical to
-- match Hibernate's generated ENUM DDL (ddl-auto: validate).
-- The Judge0 side needs the matching custom language (id 200) — see
-- deploy/judge0/README.md.
ALTER TABLE submissions
    MODIFY COLUMN language ENUM ('C','CPP','JAVA','JAVASCRIPT','PYPY3','PYTHON3') NOT NULL;
