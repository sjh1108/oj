package dev.algoj.domain.problem.dto;

/**
 * Lightweight status for chunked uploads. Intentionally does NOT echo the
 * test-case data back — same philosophy as {@link GenerateTestCaseResponse}:
 * large input/output never travels back to the browser.
 */
public record TestCaseUploadStatusResponse(
        Long id,
        long inputLength,
        long expectedOutputLength,
        boolean draft
) {
}
