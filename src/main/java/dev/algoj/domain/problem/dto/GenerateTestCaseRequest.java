package dev.algoj.domain.problem.dto;

import dev.algoj.domain.submission.entity.Submission.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Generate a single test case server-side by running a generator (produces the input)
 * and a model solution (produces the expected output) through Judge0.
 */
public record GenerateTestCaseRequest(
        @NotNull(message = "generatorLanguage는 필수입니다.")
        Language generatorLanguage,

        @NotBlank(message = "generatorCode는 필수입니다.")
        String generatorCode,

        // Optional: fed to the generator's stdin (e.g. a seed or parameters).
        String generatorStdin,

        @NotNull(message = "solutionLanguage는 필수입니다.")
        Language solutionLanguage,

        @NotBlank(message = "solutionCode는 필수입니다.")
        String solutionCode,

        @NotNull(message = "orderIndex는 필수입니다.")
        Integer orderIndex,

        @NotNull(message = "isSample은 필수입니다.")
        Boolean isSample
) {
}
