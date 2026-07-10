package dev.algoj.domain.problem.repository;

/**
 * Projection for ownership/draft checks and upload status without pulling the
 * LONGTEXT input/expectedOutput LOBs into memory on every chunk request.
 */
public interface TestCaseUploadMeta {

    Long getId();

    Long getProblemId();

    Boolean getIsDraft();

    Long getInputLength();

    Long getExpectedOutputLength();
}
