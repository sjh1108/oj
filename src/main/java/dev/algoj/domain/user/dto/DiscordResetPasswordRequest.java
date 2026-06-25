package dev.algoj.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/** Bot -> backend: reset the password of the OJ account linked to this Discord user. */
public record DiscordResetPasswordRequest(
        @NotBlank(message = "discordUserId는 필수입니다.")
        String discordUserId
) {
}
