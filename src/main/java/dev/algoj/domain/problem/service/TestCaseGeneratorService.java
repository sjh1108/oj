package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.dto.GenerateTestCaseRequest;
import dev.algoj.domain.problem.dto.GenerateTestCaseResponse;
import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.problem.repository.TestCaseRepository;
import dev.algoj.domain.submission.entity.Submission.Language;
import dev.algoj.global.client.Judge0Client;
import dev.algoj.global.client.Judge0Results;
import dev.algoj.global.client.dto.Judge0SubmissionRequest;
import dev.algoj.global.client.dto.Judge0SubmissionResponse;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a test case by running an uploaded generator (produces the input) and a model
 * solution (produces the expected output) through Judge0. Only small code is sent over
 * HTTP; the large input/output is produced and stored server-side.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseGeneratorService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final Judge0Client judge0Client;

    // Generous, separate limits for generator/solution — not the contestant problem limits.
    @Value("${judge0.generate.time-limit-ms}")
    private int generateTimeLimitMs;

    @Value("${judge0.generate.memory-limit-kb}")
    private int generateMemoryLimitKb;

    @Value("${judge0.generate.max-file-size-kb}")
    private int generateMaxFileSizeKb;

    @Transactional
    public GenerateTestCaseResponse generate(Long problemId, GenerateTestCaseRequest req) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // 1) Run the generator → its stdout becomes the test case input.
        Judge0SubmissionResponse genRes = runViaJudge0(
                req.generatorCode(),
                req.generatorLanguage(),
                req.generatorStdin() != null ? req.generatorStdin() : "");
        if (!Judge0Results.ranSuccessfully(genRes)) {
            throw new BusinessException(ErrorCode.GENERATOR_FAILED, describeFailure(genRes));
        }
        String input = genRes.stdout() != null ? genRes.stdout() : "";

        // 2) Run the model solution with that input → its stdout becomes the expected output.
        Judge0SubmissionResponse solRes = runViaJudge0(
                req.solutionCode(),
                req.solutionLanguage(),
                input);
        if (!Judge0Results.ranSuccessfully(solRes)) {
            throw new BusinessException(ErrorCode.SOLUTION_FAILED, describeFailure(solRes));
        }
        String expectedOutput = solRes.stdout() != null ? solRes.stdout() : "";

        // 3) Optionally run an independent correct solution against the new case.
        //    Judge0 does the output comparison (status 3 = Accepted), so the check
        //    uses exactly the same semantics as real judging.
        Integer validatorRuntimeMs = null;
        if (req.validatorCode() != null && !req.validatorCode().isBlank()) {
            Language validatorLanguage = req.validatorLanguage() != null
                    ? req.validatorLanguage()
                    : req.solutionLanguage();
            Judge0SubmissionResponse valRes = runViaJudge0(
                    req.validatorCode(), validatorLanguage, input, expectedOutput);
            if (valRes.status().id() != 3) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, describeFailure(valRes));
            }
            validatorRuntimeMs = valRes.runtimeMs();
        }

        // 4) Persist as a regular test case.
        TestCase tc = TestCase.builder()
                .input(input)
                .expectedOutput(expectedOutput)
                .orderIndex(req.orderIndex())
                .isSample(req.isSample())
                .build();
        problem.addTestCase(tc);
        TestCase saved = testCaseRepository.save(tc);

        return GenerateTestCaseResponse.from(saved, genRes.runtimeMs(), solRes.runtimeMs(), validatorRuntimeMs);
    }

    private Judge0SubmissionResponse runViaJudge0(String sourceCode, Language language, String stdin) {
        return runViaJudge0(sourceCode, language, stdin, null);
    }

    private Judge0SubmissionResponse runViaJudge0(String sourceCode, Language language, String stdin, String expectedOutput) {
        Judge0SubmissionRequest req = Judge0SubmissionRequest.of(
                sourceCode,
                language.getJudge0Id(),
                stdin,
                expectedOutput,
                generateTimeLimitMs,
                generateMemoryLimitKb,
                generateMaxFileSizeKb
        );
        return judge0Client.submitAndWait(req);
    }

    private String describeFailure(Judge0SubmissionResponse res) {
        String status = Judge0Results.mapStatus(res.status().id());
        String detail = Judge0Results.pickErrorMessage(res);
        return detail != null ? status + ": " + detail : status;
    }
}
