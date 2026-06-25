package dev.algoj.domain.user.dto;

/** Bot <- backend: the temporary password to show the requester (ephemeral). */
public record DiscordResetPasswordResult(
        String username,
        String temporaryPassword
) {
}
