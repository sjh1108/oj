package dev.algoj.domain.user.dto;

import jakarta.validation.constraints.NotNull;

// 전체 교체(PUT) — 프론트는 항상 모든 설정을 담아 보낸다.
public record UpdateUserPreferencesRequest(
        @NotNull(message = "showTags는 필수입니다")
        Boolean showTags
) {
}
