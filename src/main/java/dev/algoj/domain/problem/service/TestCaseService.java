package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.dto.TestCaseRequest;
import dev.algoj.domain.problem.dto.TestCaseResponse;
import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.problem.repository.TestCaseRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    @Transactional
    public TestCaseResponse add(Long problemId, TestCaseRequest req) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        TestCase tc = TestCase.builder()
                .input(req.input())
                .expectedOutput(req.expectedOutput())
                .orderIndex(req.orderIndex())
                .isSample(req.isSample())
                .build();
        problem.addTestCase(tc);
        TestCase saved = testCaseRepository.save(tc);
        return TestCaseResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TestCaseResponse> listAll(Long problemId) {
        if (!problemRepository.existsById(problemId)) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        return testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problemId).stream()
                .map(TestCaseResponse::from)
                .toList();
    }

    @Transactional
    public TestCaseResponse update(Long problemId, Long testCaseId, TestCaseRequest req) {
        TestCase tc = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEST_CASE_NOT_FOUND));
        if (!tc.getProblem().getId().equals(problemId)) {
            throw new BusinessException(ErrorCode.TEST_CASE_NOT_BELONG_TO_PROBLEM);
        }
        tc.update(req.input(), req.expectedOutput(), req.orderIndex(), req.isSample());
        return TestCaseResponse.from(tc);
    }

    @Transactional
    public void delete(Long problemId, Long testCaseId) {
        TestCase tc = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEST_CASE_NOT_FOUND));
        if (!tc.getProblem().getId().equals(problemId)) {
            throw new BusinessException(ErrorCode.TEST_CASE_NOT_BELONG_TO_PROBLEM);
        }
        testCaseRepository.delete(tc);
    }
}
