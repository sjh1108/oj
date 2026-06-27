package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.Subtask;

/** Subtask metadata shown to solvers (label + points), without exposing hidden test data. */
public record SubtaskInfoResponse(
        Long id,
        String label,
        Integer points,
        Integer orderIndex
) {
    public static SubtaskInfoResponse from(Subtask st) {
        return new SubtaskInfoResponse(st.getId(), st.getLabel(), st.getPoints(), st.getOrderIndex());
    }
}
