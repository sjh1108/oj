package dev.algoj.domain.submission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.Subtask;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.submission.entity.Submission;
import dev.algoj.domain.submission.repository.SubmissionRepository;
import dev.algoj.global.client.Judge0Client;
import dev.algoj.global.client.dto.Judge0SubmissionRequest;
import dev.algoj.global.client.dto.Judge0SubmissionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeServiceSubtaskTest {

    @Mock
    SubmissionRepository submissionRepository;
    @Mock
    Judge0Client judge0Client;

    JudgeService service;

    @BeforeEach
    void setUp() {
        service = new JudgeService(submissionRepository, judge0Client, new ObjectMapper());
    }

    private static final int AC = 3;   // Judge0 "Accepted"
    private static final int WA = 4;   // Judge0 "Wrong Answer"

    private Judge0SubmissionResponse judge0(int statusId) {
        return new Judge0SubmissionResponse(
                "out", null, null, null, "0.01", 1000, "tok",
                new Judge0SubmissionResponse.Status(statusId, "desc"));
    }

    private Problem problemWithTwoSubtasks() {
        Problem problem = Problem.builder()
                .title("p").description("d")
                .timeLimit(1000).memoryLimit(256000)
                .difficulty(Problem.Difficulty.SILVER)
                .isPublic(true)
                .build();

        Subtask st1 = Subtask.builder().label("ST1").points(30).orderIndex(0).build();
        problem.addSubtask(st1);
        st1.addTestCase(TestCase.builder().input("1").expectedOutput("1").orderIndex(0).isSample(true).build());

        Subtask st2 = Subtask.builder().label("ST2").points(70).orderIndex(1).build();
        problem.addSubtask(st2);
        st2.addTestCase(TestCase.builder().input("2").expectedOutput("2").orderIndex(1).isSample(false).build());
        st2.addTestCase(TestCase.builder().input("3").expectedOutput("3").orderIndex(2).isSample(false).build());

        return problem;
    }

    private Submission submissionFor(Problem problem, int totalTcs) {
        return Submission.builder()
                .problem(problem)
                .sourceCode("code")
                .language(Submission.Language.PYTHON3)
                .status(Submission.Status.PENDING)
                .totalTestCases(totalTcs)
                .build();
    }

    @Test
    void partialScore_whenOneSubtaskPassesAndAnotherFails() {
        Submission s = submissionFor(problemWithTwoSubtasks(), 3);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(s));
        // ST1 (1 tc) → AC; ST2 first tc → WA (breaks that subtask).
        when(judge0Client.submitAndWait(any(Judge0SubmissionRequest.class)))
                .thenReturn(judge0(AC), judge0(WA));

        service.judge(1L);

        assertThat(s.getScore()).isEqualTo(30);
        assertThat(s.getMaxScore()).isEqualTo(100);
        assertThat(s.getStatus()).isEqualTo(Submission.Status.PARTIAL);
        assertThat(s.getPassedTestCases()).isEqualTo(1);
        assertThat(s.getSubtaskResultsJson()).contains("ST1").contains("ST2");
    }

    @Test
    void fullScore_whenAllSubtasksPass() {
        Submission s = submissionFor(problemWithTwoSubtasks(), 3);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(s));
        when(judge0Client.submitAndWait(any(Judge0SubmissionRequest.class)))
                .thenReturn(judge0(AC));

        service.judge(1L);

        assertThat(s.getScore()).isEqualTo(100);
        assertThat(s.getMaxScore()).isEqualTo(100);
        assertThat(s.getStatus()).isEqualTo(Submission.Status.ACCEPTED);
        assertThat(s.getPassedTestCases()).isEqualTo(3);
    }

    @Test
    void zeroScore_whenFirstSubtaskFails_usesFailureStatus() {
        Submission s = submissionFor(problemWithTwoSubtasks(), 3);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(s));
        // ST1 first tc → WA (0), ST2 both pass → 70. So actually partial; to force zero,
        // make every test case fail.
        when(judge0Client.submitAndWait(any(Judge0SubmissionRequest.class)))
                .thenReturn(judge0(WA));

        service.judge(1L);

        assertThat(s.getScore()).isEqualTo(0);
        assertThat(s.getStatus()).isEqualTo(Submission.Status.WRONG_ANSWER);
    }

    @Test
    void legacyProblem_withoutSubtasks_scoresOutOf100() {
        Problem problem = Problem.builder()
                .title("p").description("d")
                .timeLimit(1000).memoryLimit(256000)
                .difficulty(Problem.Difficulty.BRONZE)
                .isPublic(true)
                .build();
        problem.addTestCase(TestCase.builder().input("1").expectedOutput("1").orderIndex(0).isSample(true).build());
        problem.addTestCase(TestCase.builder().input("2").expectedOutput("2").orderIndex(1).isSample(false).build());

        Submission s = submissionFor(problem, 2);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(s));
        when(judge0Client.submitAndWait(any(Judge0SubmissionRequest.class)))
                .thenReturn(judge0(AC));

        service.judge(1L);

        assertThat(s.getMaxScore()).isEqualTo(100);
        assertThat(s.getScore()).isEqualTo(100);
        assertThat(s.getStatus()).isEqualTo(Submission.Status.ACCEPTED);
    }
}
