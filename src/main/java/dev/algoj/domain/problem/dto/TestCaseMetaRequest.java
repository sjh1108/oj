package dev.algoj.domain.problem.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Order/sample flags only. Lets the admin page reorder or re-flag a case it has
 * not downloaded, instead of round-tripping a multi-MB body through
 * {@link TestCaseRequest} just to flip a boolean.
 */
public record TestCaseMetaRequest(
        @NotNull(message = "orderIndex는 필수입니다.") Integer orderIndex,
        @NotNull(message = "isSample은 필수입니다.") Boolean isSample
) {
}
