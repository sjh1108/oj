-- Draft test cases are mid-(chunked)-upload: the admin import flow creates the
-- row empty, appends input/expected_output in <1MB chunks, then finalizes.
-- Drafts are excluded from judging, sample display, and test-case counts.
ALTER TABLE test_cases
    ADD COLUMN is_draft BIT NOT NULL DEFAULT 0;
