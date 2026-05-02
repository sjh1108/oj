package dev.algoj.domain.submission.service;

import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.submission.entity.Submission;
import dev.algoj.domain.submission.repository.SubmissionRepository;
import dev.algoj.global.client.Judge0Client;
import dev.algoj.global.client.Judge0StatusMapper;
import dev.algoj.global.client.dto.Judge0SubmissionRequest;
import dev.algoj.global.client.dto.Judge0SubmissionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeAsyncService {

    private final SubmissionRepository submissionRepository;
    private final Judge0Client judge0Client;

    @Async("judgeExecutor")
    @Transactional
    public void judge(Long submissionId) {
        Submission s = submissionRepository.findById(submissionId).orElse(null);
        if (s == null) {
            log.warn("Submission {} not found for judging", submissionId);
            return;
        }
        try {
            doJudge(s);
        } catch (Exception e) {
            log.error("Judging submission {} failed", submissionId, e);
            s.updateResult(Submission.Status.SYSTEM_ERROR, null, null, e.getMessage());
        }
    }

    private void doJudge(Submission s) {
        Problem problem = s.getProblem();
        List<TestCase> testCases = problem.getTestCases().stream()
                .sorted(Comparator.comparing(TestCase::getOrderIndex))
                .toList();

        s.markJudging();
        submissionRepository.flush();

        Submission.Status finalStatus = Submission.Status.ACCEPTED;
        String errorMessage = null;

        for (TestCase tc : testCases) {
            Judge0SubmissionRequest req = Judge0SubmissionRequest.of(
                    s.getSourceCode(),
                    s.getLanguage().getJudge0Id(),
                    tc.getInput(),
                    tc.getExpectedOutput(),
                    problem.getTimeLimit(),
                    problem.getMemoryLimit()
            );
            Judge0SubmissionResponse res = judge0Client.submitAndWait(req);
            Submission.Status tcStatus = Judge0StatusMapper.toSubmissionStatus(res.status().id());

            if (tcStatus == Submission.Status.ACCEPTED) {
                s.incrementPassed(res.runtimeMs(), res.memory());
                submissionRepository.flush();
            } else {
                finalStatus = tcStatus;
                errorMessage = pickErrorMessage(res);
                break;
            }
        }

        s.updateResult(finalStatus, null, null, errorMessage);
    }

    private String pickErrorMessage(Judge0SubmissionResponse res) {
        if (res.compileOutput() != null && !res.compileOutput().isBlank()) return truncate(res.compileOutput());
        if (res.stderr() != null && !res.stderr().isBlank()) return truncate(res.stderr());
        if (res.message() != null && !res.message().isBlank()) return truncate(res.message());
        return null;
    }

    private String truncate(String s) {
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
