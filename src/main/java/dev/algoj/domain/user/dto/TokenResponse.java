package dev.algoj.domain.user.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static TokenResponse of(String accessToken, String refreshToken, long expiresInMs) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInMs / 1000L);
    }
}
