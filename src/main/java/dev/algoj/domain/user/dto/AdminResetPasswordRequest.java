package dev.algoj.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminResetPasswordRequest(
        @NotBlank(message = "usernameOrEmail은 필수입니다.")
        String usernameOrEmail
) {
}
