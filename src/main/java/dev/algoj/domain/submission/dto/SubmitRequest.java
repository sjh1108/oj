package dev.algoj.domain.submission.dto;

import dev.algoj.domain.submission.entity.Submission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitRequest(
        @NotNull(message = "problemId는 필수입니다.")
        Long problemId,

        @NotNull(message = "language는 필수입니다.")
        Submission.Language language,

        @NotBlank(message = "sourceCode는 필수입니다.")
        @Size(max = 65535, message = "sourceCode는 최대 65535자까지 가능합니다.")
        String sourceCode
) {
}
