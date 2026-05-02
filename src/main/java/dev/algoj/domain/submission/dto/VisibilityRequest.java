package dev.algoj.domain.submission.dto;

import jakarta.validation.constraints.NotNull;

public record VisibilityRequest(
        @NotNull(message = "isPublic는 필수입니다.")
        Boolean isPublic
) {
}
