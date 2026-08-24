package dev.algoj.domain.problem.controller;

import dev.algoj.domain.problem.dto.ProblemNoteResponse;
import dev.algoj.domain.problem.dto.UpdateProblemNoteRequest;
import dev.algoj.domain.problem.service.ProblemNoteService;
import dev.algoj.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문제별 개인 메모. 언제나 로그인한 본인의 메모만 오간다 — 남의 메모를 가리키는
 * 경로 자체가 없다.
 */
@RestController
@RequestMapping("/api/problems/{problemId}/note")
@RequiredArgsConstructor
public class ProblemNoteController {

    private final ProblemNoteService problemNoteService;

    @GetMapping
    public ResponseEntity<ProblemNoteResponse> get(
            @PathVariable Long problemId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(problemNoteService.get(principal.getId(), problemId));
    }

    /** 빈 본문은 삭제를 뜻한다 — 별도 DELETE 엔드포인트를 두지 않는다. */
    @PutMapping
    public ResponseEntity<ProblemNoteResponse> update(
            @PathVariable Long problemId,
            @Valid @RequestBody UpdateProblemNoteRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                problemNoteService.update(principal.getId(), problemId, request));
    }
}
