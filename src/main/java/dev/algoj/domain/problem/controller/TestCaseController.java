package dev.algoj.domain.problem.controller;

import dev.algoj.domain.problem.dto.TestCaseRequest;
import dev.algoj.domain.problem.dto.TestCaseResponse;
import dev.algoj.domain.problem.service.TestCaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/problems/{problemId}/test-cases")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TestCaseController {

    private final TestCaseService testCaseService;

    @PostMapping
    public ResponseEntity<TestCaseResponse> add(
            @PathVariable Long problemId,
            @Valid @RequestBody TestCaseRequest request) {
        TestCaseResponse response = testCaseService.add(problemId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TestCaseResponse>> listAll(@PathVariable Long problemId) {
        return ResponseEntity.ok(testCaseService.listAll(problemId));
    }

    @DeleteMapping("/{testCaseId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long problemId,
            @PathVariable Long testCaseId) {
        testCaseService.delete(problemId, testCaseId);
        return ResponseEntity.noContent().build();
    }
}
