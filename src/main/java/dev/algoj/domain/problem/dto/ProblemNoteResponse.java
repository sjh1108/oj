package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.ProblemNote;

import java.time.LocalDateTime;

/** 메모가 없으면 content가 null이다 — 빈 문자열과 구분하지 않는다. */
public record ProblemNoteResponse(
        String content,
        LocalDateTime updatedAt
) {
    public static ProblemNoteResponse from(ProblemNote note) {
        return new ProblemNoteResponse(note.getContent(), note.getUpdatedAt());
    }

    public static ProblemNoteResponse empty() {
        return new ProblemNoteResponse(null, null);
    }
}
