package dev.algoj.domain.submission.dto;

import dev.algoj.domain.submission.entity.Submission;

import java.time.LocalDateTime;

public record SubmissionResponse(
        Long id,
        Long problemId,
        String problemTitle,
        String username,
        Submission.Language language,
        Submission.Status status,
        Integer runtime,
        Integer memory,
        Integer passedTestCases,
        Integer totalTestCases,
        Integer score,
        Integer maxScore,
        Boolean isPublic,
        LocalDateTime createdAt
) {
    public static SubmissionResponse from(Submission s) {
        return new SubmissionResponse(
                s.getId(),
                s.getProblem().getId(),
                s.getProblem().getTitle(),
                s.getUser().getUsername(),
                s.getLanguage(),
                s.getStatus(),
                s.getRuntime(),
                s.getMemory(),
                s.getPassedTestCases(),
                s.getTotalTestCases(),
                s.getScore(),
                s.getMaxScore(),
                s.getIsPublic(),
                s.getCreatedAt()
        );
    }
}
