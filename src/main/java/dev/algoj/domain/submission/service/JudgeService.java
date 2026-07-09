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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Grades one submission against Judge0. Runs on RabbitMQ listener threads
 * (see {@link JudgeQueueListener}); exceptions are swallowed into SYSTEM_ERROR
 * so a poison submission never loops back onto the queue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeService {

    private static final int DEFAULT_PROBLEM_POINTS = 100;

    private final SubmissionRepository submissionRepository;
    private final Judge0Client judge0Client;
    private final ObjectMapper objectMapper;

    // Contestant runs may legitimately print multi-MB outputs for generated
    // test data — Judge0's default 1024KB fsize would truncate them.
    @Value("${judge0.judge.max-file-size-kb}")
    private int judgeMaxFileSizeKb;

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

    /** A scored group of test cases — a real subtask, or the implicit whole-problem group. */
    private record Group(String label, int points, List<TestCase> testCases) {}

    private void doJudge(Submission s) {
        Problem problem = s.getProblem();
        List<Group> groups = buildGroups(problem);

        s.markJudging();
        submissionRepository.flush();

        int maxScore = groups.stream().mapToInt(Group::points).sum();
        int totalScore = 0;
        // First failing test case overall — used as the status when nothing scores.
        Submission.Status firstFailure = null;
        String errorMessage = null;
        List<SubtaskResultDto> results = new ArrayList<>();

        for (Group group : groups) {
            boolean passed = true;
            Submission.Status groupStatus = Submission.Status.ACCEPTED;

            for (TestCase tc : group.testCases()) {
                Judge0SubmissionRequest req = Judge0SubmissionRequest.of(
                        s.getSourceCode(),
                        s.getLanguage().getJudge0Id(),
                        tc.getInput(),
                        tc.getExpectedOutput(),
                        problem.getTimeLimit(),
                        problem.getMemoryLimit(),
                        judgeMaxFileSizeKb
                );
                Judge0SubmissionResponse res = judge0Client.submitAndWait(req);
                Submission.Status tcStatus = Judge0StatusMapper.toSubmissionStatus(res.status().id());

                if (tcStatus == Submission.Status.ACCEPTED) {
                    s.incrementPassed(res.runtimeMs(), res.memory());
                    submissionRepository.flush();
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

        s.updateScore(totalScore, maxScore, toJson(results));
        s.updateResult(finalStatus, null, null, errorMessage);
    }

    /** Real subtasks if defined, else one implicit group holding every test case. */
    private List<Group> buildGroups(Problem problem) {
        List<Subtask> subtasks = problem.getSubtasks();
        if (subtasks != null && !subtasks.isEmpty()) {
            return subtasks.stream()
                    .sorted(Comparator.comparing(Subtask::getOrderIndex))
                    .map(st -> new Group(
                            st.getLabel(),
                            st.getPoints(),
                            st.getTestCases().stream()
                                    .filter(tc -> !Boolean.TRUE.equals(tc.getIsDraft()))
                                    .sorted(Comparator.comparing(TestCase::getOrderIndex))
                                    .toList()))
                    .toList();
        }
        List<TestCase> all = problem.getActiveTestCases().stream()
                .sorted(Comparator.comparing(TestCase::getOrderIndex))
                .toList();
        return List.of(new Group("전체", DEFAULT_PROBLEM_POINTS, all));
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
