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
        // 정답 처리 시점의 메모. 본인은 언제나 보고, 남에게는 그때 공개로 두었을
        // 때만 실린다. 이 화면 자체가 이미 "공개 + 정답 + 보는 사람도 그 문제를
        // 해결"일 때만 열리므로, 공개 메모라도 아직 못 푼 사람에게는 닿지 않는다.
        String noteSnapshot,
        // 그 메모가 공개 상태인지 — 본인 화면에서 "공개됨" 표시를 하기 위한 값.
        boolean noteSnapshotPublic,
        LocalDateTime createdAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** viewerIsOwner=false면 공개로 둔 메모만 실린다. */
    public static SubmissionDetailResponse from(Submission s, boolean viewerIsOwner) {
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
                noteVisibleTo(s, viewerIsOwner) ? s.getNoteSnapshot() : null,
                Boolean.TRUE.equals(s.getNoteSnapshotPublic()),
                s.getCreatedAt()
        );
    }

    private static boolean noteVisibleTo(Submission s, boolean viewerIsOwner) {
        return viewerIsOwner || Boolean.TRUE.equals(s.getNoteSnapshotPublic());
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
