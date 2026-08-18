package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.dto.AppendTestCaseChunkRequest;
import dev.algoj.domain.problem.dto.TestCaseMetaRequest;
import dev.algoj.domain.problem.dto.TestCaseRequest;
import dev.algoj.domain.problem.dto.TestCaseResponse;
import dev.algoj.domain.problem.dto.TestCaseSummaryResponse;
import dev.algoj.domain.problem.dto.TestCaseUploadStatusResponse;
import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.problem.repository.TestCaseRepository;
import dev.algoj.domain.problem.repository.TestCaseUploadMeta;
import dev.algoj.domain.problem.repository.TestCaseSummary;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestCaseService {

    /** Head of the data carried by the list endpoint — enough to eyeball a case. */
    public static final int PREVIEW_CHARS = 2000;

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
                .isDraft(req.draft())
                .build();
        problem.addTestCase(tc);
        TestCase saved = testCaseRepository.save(tc);
        return TestCaseResponse.from(saved);
    }

    @Transactional
    public TestCaseUploadStatusResponse appendChunk(Long problemId, Long testCaseId, AppendTestCaseChunkRequest req) {
        TestCaseUploadMeta meta = requireMeta(problemId, testCaseId);
        if (!Boolean.TRUE.equals(meta.getIsDraft())) {
            throw new BusinessException(ErrorCode.TEST_CASE_NOT_DRAFT);
        }
        if (req.inputChunk() != null && !req.inputChunk().isEmpty()) {
            testCaseRepository.appendInput(testCaseId, req.inputChunk());
        }
        if (req.expectedOutputChunk() != null && !req.expectedOutputChunk().isEmpty()) {
            testCaseRepository.appendExpectedOutput(testCaseId, req.expectedOutputChunk());
        }
        return uploadStatus(testCaseId);
    }

    /** Marks a draft complete so it starts being judged. Idempotent on non-drafts. */
    @Transactional
    public TestCaseUploadStatusResponse finalizeUpload(Long problemId, Long testCaseId) {
        requireMeta(problemId, testCaseId);
        testCaseRepository.clearDraft(testCaseId);
        return uploadStatus(testCaseId);
    }

    private TestCaseUploadMeta requireMeta(Long problemId, Long testCaseId) {
        TestCaseUploadMeta meta = testCaseRepository.findUploadMetaById(testCaseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEST_CASE_NOT_FOUND));
        if (!meta.getProblemId().equals(problemId)) {
            throw new BusinessException(ErrorCode.TEST_CASE_NOT_BELONG_TO_PROBLEM);
        }
        return meta;
    }

    private TestCaseUploadStatusResponse uploadStatus(Long testCaseId) {
        TestCaseUploadMeta meta = testCaseRepository.findUploadMetaById(testCaseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEST_CASE_NOT_FOUND));
        return new TestCaseUploadStatusResponse(
                meta.getId(),
                meta.getInputLength() != null ? meta.getInputLength() : 0L,
                meta.getExpectedOutputLength() != null ? meta.getExpectedOutputLength() : 0L,
                Boolean.TRUE.equals(meta.getIsDraft()));
    }

    /**
     * Lists cases without their data. Generated cases run into the megabytes, and
     * shipping every one of them at once is what made the admin page unusable.
     */
    @Transactional(readOnly = true)
    public List<TestCaseSummaryResponse> listSummaries(Long problemId) {
        if (!problemRepository.existsById(problemId)) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        List<TestCaseSummary> summaries =
                testCaseRepository.findSummariesByProblemId(problemId, PREVIEW_CHARS);
        return summaries.stream().map(TestCaseSummaryResponse::from).toList();
    }

    /** Full data for one case — only when the admin explicitly opens it. */
    @Transactional(readOnly = true)
    public TestCaseResponse get(Long problemId, Long testCaseId) {
        TestCase tc = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEST_CASE_NOT_FOUND));
        if (!tc.getProblem().getId().equals(problemId)) {
            throw new BusinessException(ErrorCode.TEST_CASE_NOT_BELONG_TO_PROBLEM);
        }
        return TestCaseResponse.from(tc);
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

    /** Order/sample only — never pulls the case data into memory. */
    @Transactional
    public void updateMeta(Long problemId, Long testCaseId, TestCaseMetaRequest req) {
        requireMeta(problemId, testCaseId);
        testCaseRepository.updateMeta(testCaseId, req.orderIndex(), req.isSample());
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
