package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.ProblemNote;

import java.time.LocalDateTime;

/** 메모가 없으면 content가 null이고 isPublic은 false다 — 공개할 것도 없다. */
public record ProblemNoteResponse(
        String content,
        boolean isPublic,
        LocalDateTime updatedAt
) {
    public static ProblemNoteResponse from(ProblemNote note) {
        return new ProblemNoteResponse(note.getContent(), note.isPublic(), note.getUpdatedAt());
    }

    public static ProblemNoteResponse empty() {
        return new ProblemNoteResponse(null, false, null);
    }
}
