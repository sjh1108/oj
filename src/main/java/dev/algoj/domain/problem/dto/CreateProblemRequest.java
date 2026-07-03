package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.Problem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreateProblemRequest(
        @NotBlank(message = "title은 필수입니다.")
        @Size(max = 200, message = "title은 최대 200자까지 가능합니다.")
        String title,

        @NotBlank(message = "description은 필수입니다.")
        String description,

        String inputDescription,
        String outputDescription,

        @NotNull(message = "timeLimit(ms)은 필수입니다.")
        @Min(value = 100, message = "timeLimit은 최소 100ms 이상이어야 합니다.")
        @Max(value = 60000, message = "timeLimit은 최대 60000ms입니다.")
        Integer timeLimit,

        @NotNull(message = "memoryLimit(KB)은 필수입니다.")
        @Min(value = 1024, message = "memoryLimit은 최소 1024KB 이상이어야 합니다.")
        @Max(value = 1048576, message = "memoryLimit은 최대 1048576KB입니다.")
        Integer memoryLimit,

        @NotNull(message = "difficulty는 필수입니다.")
        Problem.Difficulty difficulty,

        @Size(max = 10, message = "태그는 최대 10개까지 가능합니다.")
        List<@NotBlank @Size(max = 30, message = "태그는 최대 30자까지 가능합니다.") String> tags,

        @NotNull(message = "isPublic은 필수입니다.")
        Boolean isPublic,

        // Flat test cases for problems without subtasks (legacy / simple).
        @Valid
        List<TestCaseRequest> testCases,

        // When non-empty, the problem is graded by subtask (each group all-or-nothing).
        @Valid
        List<SubtaskRequest> subtasks
) {
}
