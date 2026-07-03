package dev.algoj.domain.submission.controller;

import dev.algoj.domain.submission.dto.SubmissionDetailResponse;
import dev.algoj.domain.submission.dto.SubmissionResponse;
import dev.algoj.domain.submission.dto.SubmitRequest;
import dev.algoj.domain.submission.dto.VisibilityRequest;
import dev.algoj.domain.submission.service.JudgeQueueProducer;
import dev.algoj.domain.submission.service.SubmissionService;
import dev.algoj.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final JudgeQueueProducer judgeQueueProducer;

    @PostMapping
    public ResponseEntity<SubmissionResponse> submit(
            @Valid @RequestBody SubmitRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SubmissionResponse response = submissionService.createPending(request, principal);
        judgeQueueProducer.enqueue(response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<SubmissionResponse>> listAll(
            @PageableDefault(size = 30, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(submissionService.listAll(pageable));
    }

    @GetMapping("/me")
    public ResponseEntity<Page<SubmissionResponse>> listMine(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(submissionService.listMine(principal, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionDetailResponse> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(submissionService.detail(id, principal));
    }

    @PatchMapping("/{id}/visibility")
    public ResponseEntity<SubmissionResponse> updateVisibility(
            @PathVariable Long id,
            @Valid @RequestBody VisibilityRequest body,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                submissionService.updateVisibility(id, body.isPublic(), principal));
    }

    @PostMapping("/{id}/rejudge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> rejudge(@PathVariable Long id) {
        Long resetId = submissionService.resetForRejudge(id);
        judgeQueueProducer.enqueue(resetId);
        return ResponseEntity.accepted().body(Map.of("submissionId", resetId, "queued", 1));
    }
}
