package dev.algoj.domain.run.service;

import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.run.dto.RunRequest;
import dev.algoj.domain.run.dto.RunResponse;
import dev.algoj.domain.user.entity.User;
import dev.algoj.global.client.Judge0Client;
import dev.algoj.global.client.dto.Judge0SubmissionRequest;
import dev.algoj.global.client.dto.Judge0SubmissionResponse;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import dev.algoj.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final ProblemRepository problemRepository;
    private final Judge0Client judge0Client;

    @Transactional(readOnly = true)
    public RunResponse run(RunRequest req, UserPrincipal principal) {
        Problem problem = problemRepository.findById(req.problemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        boolean isAdmin = principal.getRole() == User.Role.ADMIN;
        if (!problem.getIsPublic() && !isAdmin) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_ACCESSIBLE);
        }

        Judge0SubmissionRequest judge0Req = Judge0SubmissionRequest.of(
                req.sourceCode(),
                req.language().getJudge0Id(),
                req.stdin() != null ? req.stdin() : "",
                null,
                problem.getTimeLimit(),
                problem.getMemoryLimit()
        );

        Judge0SubmissionResponse res = judge0Client.submitAndWait(judge0Req);

        String status = mapStatus(res.status().id());
        String errorMessage = pickErrorMessage(res);

        return new RunResponse(
                status,
                res.stdout(),
                res.stderr(),
                res.compileOutput(),
                res.runtimeMs(),
                res.memory(),
                errorMessage
        );
    }

    private String mapStatus(int judge0StatusId) {
        return switch (judge0StatusId) {
            case 3, 4 -> "OK";
            case 5 -> "TIME_LIMIT";
            case 6 -> "COMPILE_ERROR";
            case 7, 8, 9, 10, 11, 12 -> "RUNTIME_ERROR";
            default -> "SYSTEM_ERROR";
        };
    }

    private String pickErrorMessage(Judge0SubmissionResponse res) {
        if (res.compileOutput() != null && !res.compileOutput().isBlank()) return truncate(res.compileOutput());
        if (res.stderr() != null && !res.stderr().isBlank()) return truncate(res.stderr());
        if (res.message() != null && !res.message().isBlank()) return truncate(res.message());
        return null;
    }

    private String truncate(String s) {
        return s.length() > MAX_ERROR_LENGTH ? s.substring(0, MAX_ERROR_LENGTH) : s;
    }
}
