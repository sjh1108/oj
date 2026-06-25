package dev.algoj.domain.problem.controller;

import dev.algoj.domain.problem.dto.GenerateTestCaseRequest;
import dev.algoj.domain.problem.dto.GenerateTestCaseResponse;
import dev.algoj.domain.problem.dto.TestCaseRequest;
import dev.algoj.domain.problem.dto.TestCaseResponse;
import dev.algoj.domain.problem.service.TestCaseGeneratorService;
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
    private final TestCaseGeneratorService testCaseGeneratorService;

    @PostMapping
    public ResponseEntity<TestCaseResponse> add(
            @PathVariable Long problemId,
            @Valid @RequestBody TestCaseRequest request) {
        TestCaseResponse response = testCaseService.add(problemId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateTestCaseResponse> generate(
            @PathVariable Long problemId,
            @Valid @RequestBody GenerateTestCaseRequest request) {
        GenerateTestCaseResponse response = testCaseGeneratorService.generate(problemId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TestCaseResponse>> listAll(@PathVariable Long problemId) {
        return ResponseEntity.ok(testCaseService.listAll(problemId));
    }

    @PutMapping("/{testCaseId}")
    public ResponseEntity<TestCaseResponse> update(
            @PathVariable Long problemId,
            @PathVariable Long testCaseId,
            @Valid @RequestBody TestCaseRequest request) {
        return ResponseEntity.ok(testCaseService.update(problemId, testCaseId, request));
    }

    @DeleteMapping("/{testCaseId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long problemId,
            @PathVariable Long testCaseId) {
        testCaseService.delete(problemId, testCaseId);
        return ResponseEntity.noContent().build();
    }
}
