package dev.algoj.domain.submission.controller;

import dev.algoj.domain.submission.dto.SubmissionDetailResponse;
import dev.algoj.domain.submission.dto.SubmissionResponse;
import dev.algoj.domain.submission.dto.SubmitRequest;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    public ResponseEntity<SubmissionDetailResponse> submit(
            @Valid @RequestBody SubmitRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SubmissionDetailResponse response = submissionService.submit(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
}
