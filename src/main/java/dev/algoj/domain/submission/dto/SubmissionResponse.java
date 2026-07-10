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
        // Judging progress in percent (0-100) while PENDING/JUDGING, else null.
        // Absolute test-case counts are deliberately not exposed (BOJ-style).
        Integer progress,
        Integer score,
        Integer maxScore,
        Boolean isPublic,
        LocalDateTime createdAt
) {
    public static SubmissionResponse from(Submission s) {
        // BOJ-style: runtime/memory only for accepted runs. Anything else would
        // expose the passed-prefix numbers — meaningless and a test-data probe.
        boolean showPerf = s.getStatus() == Submission.Status.ACCEPTED;
        return new SubmissionResponse(
                s.getId(),
                s.getProblem().getId(),
                s.getProblem().getTitle(),
                s.getUser().getUsername(),
                s.getLanguage(),
                s.getStatus(),
                showPerf ? s.getRuntime() : null,
                showPerf ? s.getMemory() : null,
                progressOf(s),
                s.getScore(),
                s.getMaxScore(),
                s.getIsPublic(),
                s.getCreatedAt()
        );
    }

    /** 0-100 while pending/judging (floor — 100 only when every case passed), null once final. */
    static Integer progressOf(Submission s) {
        if (s.getStatus() == Submission.Status.PENDING) return 0;
        if (s.getStatus() != Submission.Status.JUDGING) return null;
        Integer total = s.getTotalTestCases();
        Integer passed = s.getPassedTestCases();
        if (total == null || total <= 0 || passed == null) return 0;
        return (int) ((100L * passed) / total);
    }
}
