package dev.algoj.domain.problem.controller;

import dev.algoj.domain.problem.dto.CreateProblemRequest;
import dev.algoj.domain.problem.dto.ProblemDetailResponse;
import dev.algoj.domain.problem.dto.ProblemListResponse;
import dev.algoj.domain.problem.dto.UpdateProblemRequest;
import dev.algoj.domain.problem.service.ProblemService;
import dev.algoj.domain.submission.dto.SubmissionResponse;
import dev.algoj.domain.submission.service.JudgeAsyncService;
import dev.algoj.domain.submission.service.SubmissionService;
import dev.algoj.domain.user.entity.User;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;
    private final SubmissionService submissionService;
    private final JudgeAsyncService judgeAsyncService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProblemDetailResponse> create(
            @Valid @RequestBody CreateProblemRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ProblemDetailResponse response = problemService.create(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ProblemListResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        boolean isAdmin = principal.getRole() == User.Role.ADMIN;
        return ResponseEntity.ok(problemService.list(isAdmin, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProblemDetailResponse> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.getRole() == User.Role.ADMIN;
        return ResponseEntity.ok(problemService.detail(id, isAdmin));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProblemDetailResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProblemRequest request) {
        return ResponseEntity.ok(problemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        problemService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/solutions")
    public ResponseEntity<Page<SubmissionResponse>> solutions(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(submissionService.listSolutions(id, principal, pageable));
    }

    @PostMapping("/{id}/rejudge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> rejudge(@PathVariable Long id) {
        List<Long> ids = submissionService.resetAllForProblemRejudge(id);
        for (Long sid : ids) {
            judgeAsyncService.judge(sid);
        }
        return ResponseEntity.accepted().body(Map.of("queued", ids.size()));
    }
}
