package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.repository.TestCaseSummary;

/**
 * Test case without its full data — lengths plus the first
 * {@code previewChars} characters. The browser fetches the whole case
 * (GET /test-cases/{id}) only when the admin asks to open it.
 */
public record TestCaseSummaryResponse(
        Long id,
        Integer orderIndex,
        Boolean isSample,
        Boolean isDraft,
        long inputLength,
        long expectedOutputLength,
        String inputPreview,
        String expectedOutputPreview
) {
    public static TestCaseSummaryResponse from(TestCaseSummary s) {
        return new TestCaseSummaryResponse(
                s.getId(),
                s.getOrderIndex(),
                s.getIsSample(),
                s.getIsDraft(),
                s.getInputLength() != null ? s.getInputLength() : 0L,
                s.getExpectedOutputLength() != null ? s.getExpectedOutputLength() : 0L,
                s.getInputPreview() != null ? s.getInputPreview() : "",
                s.getExpectedOutputPreview() != null ? s.getExpectedOutputPreview() : ""
        );
    }
}
