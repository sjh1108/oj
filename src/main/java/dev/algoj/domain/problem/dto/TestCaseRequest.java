package dev.algoj.domain.problem.dto;

import jakarta.validation.constraints.NotNull;

public record TestCaseRequest(
        @NotNull(message = "input은 필수입니다.")
        String input,

        @NotNull(message = "expectedOutput은 필수입니다.")
        String expectedOutput,

        @NotNull(message = "orderIndex는 필수입니다.")
        Integer orderIndex,

        @NotNull(message = "isSample은 필수입니다.")
        Boolean isSample,

        // Optional (null → false). true creates the case as a draft so its data
        // can be appended in chunks before finalizing; drafts are never judged.
        Boolean draft
) {
}
