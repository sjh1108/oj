package dev.algoj.domain.submission.dto;

/** One subtask's outcome for a submission (persisted as JSON, shown on the detail page). */
public record SubtaskResultDto(
        String label,
        int points,
        int earned,
        boolean passed,
        String status
) {
}
