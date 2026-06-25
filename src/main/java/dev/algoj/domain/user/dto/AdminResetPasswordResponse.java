package dev.algoj.domain.user.dto;

/**
 * Returned to the admin once after a reset. Carries the plaintext temporary password
 * so the admin can relay it to the member out-of-band (it is never stored in plaintext).
 */
public record AdminResetPasswordResponse(
        Long userId,
        String username,
        String email,
        String temporaryPassword
) {
}
