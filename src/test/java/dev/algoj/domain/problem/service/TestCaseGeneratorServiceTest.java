package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.dto.GenerateTestCaseRequest;
import dev.algoj.domain.problem.dto.GenerateTestCaseResponse;
import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.problem.repository.TestCaseRepository;
import dev.algoj.domain.submission.entity.Submission.Language;
import dev.algoj.global.client.Judge0Client;
import dev.algoj.global.client.dto.Judge0SubmissionResponse;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestCaseGeneratorServiceTest {

    @Mock
    ProblemRepository problemRepository;
    @Mock
    TestCaseRepository testCaseRepository;
    @Mock
    Judge0Client judge0Client;

    @InjectMocks
    TestCaseGeneratorService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "generateTimeLimitMs", 10000);
        ReflectionTestUtils.setField(service, "generateMemoryLimitKb", 512000);
        when(problemRepository.findById(1L)).thenReturn(Optional.of(sampleProblem()));
    }

    @Test
    void generate_runsGeneratorThenSolution_andStoresTestCase() {
        // generator -> input "5\n", solution(input) -> output "15\n"
        when(judge0Client.submitAndWait(any()))
                .thenReturn(okResponse("5\n"))
                .thenReturn(okResponse("15\n"));
        when(testCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerateTestCaseResponse res = service.generate(1L, request());

        ArgumentCaptor<TestCase> saved = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseRepository).save(saved.capture());
        assertThat(saved.getValue().getInput()).isEqualTo("5\n");
        assertThat(saved.getValue().getExpectedOutput()).isEqualTo("15\n");
        assertThat(res.inputSize()).isEqualTo(2);
        assertThat(res.outputSize()).isEqualTo(3);
        verify(judge0Client, times(2)).submitAndWait(any());
    }

    @Test
    void generate_whenGeneratorFails_throwsGeneratorFailed_andDoesNotRunSolution() {
        when(judge0Client.submitAndWait(any())).thenReturn(compileErrorResponse());

        assertThatThrownBy(() -> service.generate(1L, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GENERATOR_FAILED);

        verify(judge0Client, times(1)).submitAndWait(any());
        verify(testCaseRepository, never()).save(any());
    }

    @Test
    void generate_whenSolutionFails_throwsSolutionFailed() {
        when(judge0Client.submitAndWait(any()))
                .thenReturn(okResponse("5\n"))
                .thenReturn(compileErrorResponse());

        assertThatThrownBy(() -> service.generate(1L, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOLUTION_FAILED);

        verify(testCaseRepository, never()).save(any());
    }

    // ── helpers ──────────────────────────────────────────────

    private GenerateTestCaseRequest request() {
        return new GenerateTestCaseRequest(
                Language.PYTHON3, "print(5)", null,
                Language.PYTHON3, "print(15)",
                0, false);
    }

    private Problem sampleProblem() {
        return Problem.builder()
                .title("t").description("d")
                .timeLimit(2000).memoryLimit(256000)
                .difficulty(Problem.Difficulty.BRONZE)
                .isPublic(true)
                .build();
    }

    private Judge0SubmissionResponse okResponse(String stdout) {
        return new Judge0SubmissionResponse(
                stdout, null, null, null, "0.05", 1024, "tok",
                new Judge0SubmissionResponse.Status(3, "Accepted"));
    }

    private Judge0SubmissionResponse compileErrorResponse() {
        return new Judge0SubmissionResponse(
                null, null, "SyntaxError", null, "0.0", 0, "tok",
                new Judge0SubmissionResponse.Status(6, "Compilation Error"));
    }
}
