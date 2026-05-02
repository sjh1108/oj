package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.Problem;

import java.time.LocalDateTime;

public record ProblemListResponse(
        Long id,
        String title,
        Problem.Difficulty difficulty,
        String authorUsername,
        Boolean isPublic,
        LocalDateTime createdAt
) {
    public static ProblemListResponse from(Problem p) {
        return new ProblemListResponse(
                p.getId(),
                p.getTitle(),
                p.getDifficulty(),
                p.getAuthor() != null ? p.getAuthor().getUsername() : null,
                p.getIsPublic(),
                p.getCreatedAt()
        );
    }
}
