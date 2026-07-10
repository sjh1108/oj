package dev.algoj.domain.submission.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.algoj.domain.submission.entity.Submission;

import java.time.LocalDateTime;
import java.util.List;

public record SubmissionDetailResponse(
        Long id,
        Long problemId,
        String problemTitle,
        String username,
        Submission.Language language,
        Submission.Status status,
        Integer runtime,
        Integer memory,
        // Percent progress while judging — see SubmissionResponse.progressOf.
        Integer progress,
        Integer score,
        Integer maxScore,
        List<SubtaskResultDto> subtaskResults,
        Boolean isPublic,
        String sourceCode,
        String errorMessage,
        LocalDateTime createdAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static SubmissionDetailResponse from(Submission s) {
        // BOJ-style: runtime/memory only for accepted runs (see SubmissionResponse).
        boolean showPerf = s.getStatus() == Submission.Status.ACCEPTED;
        return new SubmissionDetailResponse(
                s.getId(),
                s.getProblem().getId(),
                s.getProblem().getTitle(),
                s.getUser().getUsername(),
                s.getLanguage(),
                s.getStatus(),
                showPerf ? s.getRuntime() : null,
                showPerf ? s.getMemory() : null,
                SubmissionResponse.progressOf(s),
                s.getScore(),
                s.getMaxScore(),
                parseSubtasks(s.getSubtaskResultsJson()),
                s.getIsPublic(),
                s.getSourceCode(),
                s.getErrorMessage(),
                s.getCreatedAt()
        );
    }

    private static List<SubtaskResultDto> parseSubtasks(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<SubtaskResultDto>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
