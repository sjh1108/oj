package dev.algoj.domain.user.dto;

import dev.algoj.domain.user.entity.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        User.Role role,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
