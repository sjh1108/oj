package dev.algoj.domain.run.service;

import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.run.dto.RunRequest;
import dev.algoj.domain.run.dto.RunResponse;
import dev.algoj.domain.user.entity.User;
import dev.algoj.global.client.Judge0Client;
import dev.algoj.global.client.Judge0Results;
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

        return new RunResponse(
                Judge0Results.mapStatus(res.status().id()),
                res.stdout(),
                res.stderr(),
                res.compileOutput(),
                res.runtimeMs(),
                res.memory(),
                Judge0Results.pickErrorMessage(res)
        );
    }
}
