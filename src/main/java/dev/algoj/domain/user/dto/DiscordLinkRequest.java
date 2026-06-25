package dev.algoj.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/** Bot -> backend: bind a Discord user to an OJ account via a one-time code. */
public record DiscordLinkRequest(
        @NotBlank(message = "discordUserId는 필수입니다.")
        String discordUserId,

        @NotBlank(message = "code는 필수입니다.")
        String code
) {
}
