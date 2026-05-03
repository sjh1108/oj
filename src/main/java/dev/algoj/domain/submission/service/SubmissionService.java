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
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import dev.algoj.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;

    @Transactional
    public SubmissionResponse createPending(SubmitRequest req, UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Problem problem = problemRepository.findById(req.problemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        List<TestCase> testCases = problem.getTestCases();
        if (testCases.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_TEST_CASES);
        }

        Submission submission = Submission.builder()
                .user(user)
                .problem(problem)
                .sourceCode(req.sourceCode())
                .language(req.language())
                .status(Submission.Status.PENDING)
                .totalTestCases(testCases.size())
                .build();
        Submission saved = submissionRepository.save(submission);
        return SubmissionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> listMine(UserPrincipal principal, Pageable pageable) {
        return submissionRepository.findAllByUserId(principal.getId(), pageable)
                .map(SubmissionResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> listAll(Pageable pageable) {
        return submissionRepository.findAll(pageable).map(SubmissionResponse::from);
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> solvedProblemIds(Long userId) {
        return submissionRepository.findDistinctSolvedProblemIdsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse detail(Long id, UserPrincipal principal) {
        Submission s = submissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));
        boolean isOwner = s.getUser().getId().equals(principal.getId());
        boolean isAdmin = principal.getRole() == User.Role.ADMIN;
        boolean canViewByAcceptance =
                Boolean.TRUE.equals(s.getIsPublic())
                        && s.getStatus() == Submission.Status.ACCEPTED
                        && submissionRepository.existsByUserIdAndProblemIdAndStatus(
                                principal.getId(),
                                s.getProblem().getId(),
                                Submission.Status.ACCEPTED);

        if (!isOwner && !isAdmin && !canViewByAcceptance) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return SubmissionDetailResponse.from(s);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> listSolutions(Long problemId, UserPrincipal principal, Pageable pageable) {
        problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        boolean isAdmin = principal.getRole() == User.Role.ADMIN;
        boolean hasSolved = submissionRepository.existsByUserIdAndProblemIdAndStatus(
                principal.getId(), problemId, Submission.Status.ACCEPTED);

        if (!isAdmin && !hasSolved) {
            throw new BusinessException(ErrorCode.SOLUTION_LOCKED);
        }

        return submissionRepository
                .findAllByProblemIdAndStatusAndIsPublicTrue(
                        problemId, Submission.Status.ACCEPTED, pageable)
                .map(SubmissionResponse::from);
    }

    @Transactional
    public SubmissionResponse updateVisibility(Long id, boolean isPublic, UserPrincipal principal) {
        Submission s = submissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));
        boolean isOwner = s.getUser().getId().equals(principal.getId());
        boolean isAdmin = principal.getRole() == User.Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        s.setVisibility(isPublic);
        return SubmissionResponse.from(s);
    }

    @Transactional
    public Long resetForRejudge(Long submissionId) {
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBMISSION_NOT_FOUND));
        int total = s.getProblem().getTestCases().size();
        s.resetForRejudge(total);
        return s.getId();
    }

    @Transactional
    public List<Long> resetAllForProblemRejudge(Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        int total = problem.getTestCases().size();
        List<Submission> submissions = submissionRepository.findAllByProblemId(problemId);
        return submissions.stream()
                .peek(s -> s.resetForRejudge(total))
                .map(Submission::getId)
                .toList();
    }
}
