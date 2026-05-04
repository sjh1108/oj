package dev.algoj.domain.run.dto;

import dev.algoj.domain.submission.entity.Submission.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RunRequest(
        @NotNull(message = "problemId는 필수입니다.") Long problemId,
        @NotNull(message = "language는 필수입니다.") Language language,
        @NotBlank(message = "sourceCode는 필수입니다.") String sourceCode,
        String stdin
) {
}
