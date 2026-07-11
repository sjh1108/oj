package dev.algoj.domain.submission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.Subtask;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.submission.dto.SubtaskResultDto;
import dev.algoj.domain.submission.entity.Submission;
import dev.algoj.domain.submission.repository.SubmissionRepository;
import dev.algoj.global.client.Judge0Client;
import dev.algoj.global.client.Judge0StatusMapper;
import dev.algoj.global.client.dto.Judge0SubmissionRequest;
import dev.algoj.global.client.dto.Judge0SubmissionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Grades one submission against Judge0. Runs on RabbitMQ listener threads
 * (see {@link JudgeQueueListener}); exceptions are swallowed into SYSTEM_ERROR
 * so a poison submission never loops back onto the queue.
 *
 * Deliberately NOT one big transaction: the flip to JUDGING and each
 * passed-case increment are committed in their own short transactions so the
 * polling frontend sees live percent progress (an uncommitted flush is
 * invisible to other connections). A run interrupted between commits is healed
 * by queue redelivery + markJudging()'s counter reset.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeService {

    private static final int DEFAULT_PROBLEM_POINTS = 100;

    // Extra wait on top of the problem's time limit for Judge0 queueing and
    // multi-MB stdin/stdout transfer before the wait=true call is given up on.
    private static final int JUDGE0_WAIT_MARGIN_MS = 15_000;

    private final SubmissionRepository submissionRepository;
    private final Judge0Client judge0Client;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    // Contestant runs may legitimately print multi-MB outputs for generated
    // test data — Judge0's default 1024KB fsize would truncate them.
    @Value("${judge0.judge.max-file-size-kb}")
    private int judgeMaxFileSizeKb;

    public void judge(Long submissionId) {
        try {
            JudgePlan plan = transactionTemplate.execute(status -> loadPlan(submissionId));
            if (plan == null) {
                log.warn("Submission {} not found for judging", submissionId);
                return;
            }
            doJudge(submissionId, plan);
        } catch (Exception e) {
            log.error("Judging submission {} failed", submissionId, e);
            recordSystemError(submissionId, e.getMessage());
        }
    }

    /** Everything a judge run needs, detached from the persistence context. */
    private record PlannedCase(String input, String expectedOutput) {}
    private record PlannedGroup(String label, int points, List<PlannedCase> cases) {}
    private record JudgePlan(String sourceCode,
                             int judge0LanguageId,
                             int timeLimitMs,
                             int memoryLimitKb,
                             List<PlannedGroup> groups) {}

    private JudgePlan loadPlan(Long submissionId) {
        Submission s = submissionRepository.findById(submissionId).orElse(null);
        if (s == null) return null;
        Problem problem = s.getProblem();
        return new JudgePlan(
                s.getSourceCode(),
                s.getLanguage().getJudge0Id(),
                problem.getTimeLimit(),
                problem.getMemoryLimit(),
                buildGroups(problem));
    }

    private void doJudge(Long submissionId, JudgePlan plan) {
        int totalCases = plan.groups().stream().mapToInt(g -> g.cases().size()).sum();
        // Committed immediately — pollers flip to "채점중 0%" now. Also resets
        // counters so a redelivered run never double-counts.
        updateSubmission(submissionId, s -> s.markJudging(totalCases));

        int maxScore = plan.groups().stream().mapToInt(PlannedGroup::points).sum();
        int totalScore = 0;
        // First failing test case overall — used as the status when nothing scores.
        Submission.Status firstFailure = null;
        String errorMessage = null;
        List<SubtaskResultDto> results = new ArrayList<>();

        for (PlannedGroup group : plan.groups()) {
            boolean passed = true;
            Submission.Status groupStatus = Submission.Status.ACCEPTED;

            for (PlannedCase tc : group.cases()) {
                Judge0SubmissionRequest req = Judge0SubmissionRequest.of(
                        plan.sourceCode(),
                        plan.judge0LanguageId(),
                        tc.input(),
                        tc.expectedOutput(),
                        plan.timeLimitMs(),
                        plan.memoryLimitKb(),
                        judgeMaxFileSizeKb
                );
                Judge0SubmissionResponse res = judge0Client.submitAndWait(
                        req, plan.timeLimitMs() + JUDGE0_WAIT_MARGIN_MS);
                Submission.Status tcStatus = Judge0StatusMapper.toSubmissionStatus(res.status().id());

                if (tcStatus == Submission.Status.ACCEPTED) {
                    // Own committed transaction per case → live progress for pollers.
                    updateSubmission(submissionId, s -> s.incrementPassed(res.runtimeMs(), res.memory()));
                } else {
                    passed = false;
                    groupStatus = tcStatus;
                    if (firstFailure == null) {
                        firstFailure = tcStatus;
                        errorMessage = pickErrorMessage(res);
                    }
                    break; // this subtask is lost; move on to the next one
                }
            }

            int earned = passed ? group.points() : 0;
            totalScore += earned;
            results.add(new SubtaskResultDto(
                    group.label(), group.points(), earned, passed, groupStatus.name()));
        }

        Submission.Status finalStatus;
        if (maxScore > 0 && totalScore == maxScore) {
            finalStatus = Submission.Status.ACCEPTED;
        } else if (totalScore > 0) {
            finalStatus = Submission.Status.PARTIAL;
        } else {
            finalStatus = firstFailure != null ? firstFailure : Submission.Status.WRONG_ANSWER;
        }

        int score = totalScore;
        Submission.Status status = finalStatus;
        String message = errorMessage;
        String resultsJson = toJson(results);
        updateSubmission(submissionId, s -> {
            s.updateScore(score, maxScore, resultsJson);
            s.updateResult(status, null, null, message);
        });
    }

    /** Loads, mutates, and commits the submission in its own short transaction. */
    private void updateSubmission(Long submissionId, Consumer<Submission> mutation) {
        transactionTemplate.executeWithoutResult(status ->
                submissionRepository.findById(submissionId).ifPresent(mutation));
    }

    private void recordSystemError(Long submissionId, String message) {
        try {
            updateSubmission(submissionId, s ->
                    s.updateResult(Submission.Status.SYSTEM_ERROR, null, null, message));
        } catch (Exception e) {
            log.error("Failed to record SYSTEM_ERROR for submission {}", submissionId, e);
        }
    }

    /** Real subtasks if defined, else one implicit group holding every test case. */
    private List<PlannedGroup> buildGroups(Problem problem) {
        List<Subtask> subtasks = problem.getSubtasks();
        if (subtasks != null && !subtasks.isEmpty()) {
            return subtasks.stream()
                    .sorted(Comparator.comparing(Subtask::getOrderIndex))
                    .map(st -> new PlannedGroup(
                            st.getLabel(),
                            st.getPoints(),
                            st.getTestCases().stream()
                                    .filter(tc -> !Boolean.TRUE.equals(tc.getIsDraft()))
                                    .sorted(Comparator.comparing(TestCase::getOrderIndex))
                                    .map(tc -> new PlannedCase(tc.getInput(), tc.getExpectedOutput()))
                                    .toList()))
                    .toList();
        }
        List<PlannedCase> all = problem.getActiveTestCases().stream()
                .sorted(Comparator.comparing(TestCase::getOrderIndex))
                .map(tc -> new PlannedCase(tc.getInput(), tc.getExpectedOutput()))
                .toList();
        return List.of(new PlannedGroup("전체", DEFAULT_PROBLEM_POINTS, all));
    }

    private String toJson(List<SubtaskResultDto> results) {
        try {
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            log.warn("Failed to serialize subtask results", e);
            return null;
        }
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
