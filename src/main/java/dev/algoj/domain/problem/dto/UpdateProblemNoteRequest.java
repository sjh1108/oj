package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.ProblemNote;
import jakarta.validation.constraints.Size;

/**
 * content가 비었거나 공백뿐이면 메모를 지운다는 뜻이다(별도 DELETE 없음).
 * null도 같은 의미로 받는다 — 프론트가 빈 에디터를 비운 채로 보내도 동작하도록.
 *
 * isPublic이 null이면 비공개로 본다. 공개는 언제나 명시적 선택이어야 한다.
 */
public record UpdateProblemNoteRequest(
        @Size(max = ProblemNote.MAX_LENGTH, message = "메모는 4000자를 넘을 수 없습니다.")
        String content,

        Boolean isPublic
) {
    public boolean publicOrDefault() {
        return Boolean.TRUE.equals(isPublic);
    }
}
