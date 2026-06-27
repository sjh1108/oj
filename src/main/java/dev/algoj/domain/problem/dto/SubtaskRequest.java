package dev.algoj.domain.problem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubtaskRequest(
        String label,

        @NotNull(message = "subtask points는 필수입니다.")
        @Min(value = 0, message = "points는 0 이상이어야 합니다.")
        Integer points,

        @Valid
        @NotEmpty(message = "subtask에는 테스트케이스가 최소 1개 필요합니다.")
        List<TestCaseRequest> testCases
) {
}
