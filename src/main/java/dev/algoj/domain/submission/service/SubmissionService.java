package dev.algoj.domain.submission.service;

import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.submission.dto.SubmissionDetailResponse;
import dev.algoj.domain.submission.dto.SubmissionResponse;
import dev.algoj.domain.submission.dto.SubmitRequest;
import dev.algoj.domain.submission.entity.Submission;
import dev.algoj.domain.submission.repository.SubmissionRepository;
import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.repository.UserRepository;
import dev.algoj.global.client.Judge0Client;
import dev.algoj.global.client.Judge0StatusMapper;
import dev.algoj.global.client.dto.Judge0SubmissionRequest;
import dev.algoj.global.client.dto.Judge0SubmissionResponse;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import dev.algoj.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final Judge0Client judge0Client;

    @Transactional
    public SubmissionDetailResponse submit(SubmitRequest req, UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Problem problem = problemRepository.findById(req.problemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        List<TestCase> testCases = problem.getTestCases().stream()
                .sorted(Comparator.comparing(TestCase::getOrderIndex))
                .toList();
        if (testCases.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_TEST_CASES);
        }

        Submission submission = Submission.builder()
                .user(user)
                .problem(problem)
                .sourceCode(req.sourceCode())
                .language(req.language())
                .status(Submission.Status.JUDGING)
                .build();
        Submission saved = submissionRepository.save(submission);

        Submission.Status finalStatus = Submission.Status.ACCEPTED;
        Integer maxRuntime = 0;
        Integer maxMemory = 0;
        String errorMessage = null;

        for (TestCase tc : testCases) {
            Judge0SubmissionRequest judgeReq = Judge0SubmissionRequest.of(
                    req.sourceCode(),
                    req.language().getJudge0Id(),
                    tc.getInput(),
                    tc.getExpectedOutput(),
                    problem.getTimeLimit(),
                    problem.getMemoryLimit()
            );

            Judge0SubmissionResponse judgeRes = judge0Client.submitAndWait(judgeReq);
            Submission.Status tcStatus = Judge0StatusMapper.toSubmissionStatus(judgeRes.status().id());

            Integer runtimeMs = judgeRes.runtimeMs();
            if (runtimeMs != null) maxRuntime = Math.max(maxRuntime, runtimeMs);
            if (judgeRes.memory() != null) maxMemory = Math.max(maxMemory, judgeRes.memory());

            if (tcStatus != Submission.Status.ACCEPTED) {
                finalStatus = tcStatus;
                errorMessage = pickErrorMessage(judgeRes);
                break;
            }
        }

        saved.updateResult(finalStatus, maxRuntime, maxMemory, errorMessage);
        return SubmissionDetailResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> listMine(UserPrincipal principal, Pageable pageable) {
        return submissionRepository.findAllByUserId(principal.getId(), pageable)
                .map(SubmissionResponse::from);
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse detail(Long id, UserPrincipal principal) {
        Submission s = submissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));
        boolean isOwner = s.getUser().getId().equals(principal.getId());
        boolean isAdmin = principal.getRole() == User.Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return SubmissionDetailResponse.from(s);
    }

    private String pickErrorMessage(Judge0SubmissionResponse res) {
        if (res.compileOutput() != null && !res.compileOutput().isBlank()) {
            return truncate(res.compileOutput());
        }
        if (res.stderr() != null && !res.stderr().isBlank()) {
            return truncate(res.stderr());
        }
        if (res.message() != null && !res.message().isBlank()) {
            return truncate(res.message());
        }
        return null;
    }

    private String truncate(String s) {
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
