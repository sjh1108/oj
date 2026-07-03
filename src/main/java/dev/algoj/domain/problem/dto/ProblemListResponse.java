package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.Problem;

import java.time.LocalDateTime;
import java.util.List;

public record ProblemListResponse(
        Long id,
        String title,
        Problem.Difficulty difficulty,
        List<String> tags,
        String authorUsername,
        Boolean isPublic,
        // Requesting user's history with this problem.
        Boolean solved,
        Boolean attempted,
        LocalDateTime createdAt
) {
    public static ProblemListResponse from(Problem p, boolean solved, boolean attempted) {
        return new ProblemListResponse(
                p.getId(),
                p.getTitle(),
                p.getDifficulty(),
                p.getTags().stream().sorted().toList(),
                p.getAuthor() != null ? p.getAuthor().getUsername() : null,
                p.getIsPublic(),
                solved,
                attempted,
                p.getCreatedAt()
        );
    }
}
