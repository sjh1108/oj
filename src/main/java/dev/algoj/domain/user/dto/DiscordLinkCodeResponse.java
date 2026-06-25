package dev.algoj.domain.user.dto;

/** Returned to a logged-in user who wants to link their Discord account. */
public record DiscordLinkCodeResponse(
        String code,
        long expiresInSeconds
) {
}
