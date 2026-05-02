package dev.algoj.domain.submission.dto;

import dev.algoj.domain.submission.entity.Submission;

import java.time.LocalDateTime;

public record SubmissionDetailResponse(
        Long id,
        Long problemId,
        String problemTitle,
        String username,
        Submission.Language language,
        Submission.Status status,
        Integer runtime,
        Integer memory,
        String sourceCode,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static SubmissionDetailResponse from(Submission s) {
        return new SubmissionDetailResponse(
                s.getId(),
                s.getProblem().getId(),
                s.getProblem().getTitle(),
                s.getUser().getUsername(),
                s.getLanguage(),
                s.getStatus(),
                s.getRuntime(),
                s.getMemory(),
                s.getSourceCode(),
                s.getErrorMessage(),
                s.getCreatedAt()
        );
    }
}
