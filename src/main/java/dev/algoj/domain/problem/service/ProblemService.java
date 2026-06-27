package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.dto.*;
import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.Subtask;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.repository.UserRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import dev.algoj.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProblemDetailResponse create(CreateProblemRequest req, UserPrincipal principal) {
        User author = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Problem problem = Problem.builder()
                .title(req.title())
                .description(req.description())
                .inputDescription(req.inputDescription())
                .outputDescription(req.outputDescription())
                .timeLimit(req.timeLimit())
                .memoryLimit(req.memoryLimit())
                .difficulty(req.difficulty())
                .author(author)
                .isPublic(req.isPublic())
                .build();

        List<SubtaskRequest> subtaskReqs = req.subtasks();
        if (subtaskReqs != null && !subtaskReqs.isEmpty()) {
            // Subtask mode: each group all-or-nothing; assign a running global orderIndex.
            int order = 0;
            for (int i = 0; i < subtaskReqs.size(); i++) {
                SubtaskRequest stReq = subtaskReqs.get(i);
                String label = (stReq.label() != null && !stReq.label().isBlank())
                        ? stReq.label() : "서브태스크 " + (i + 1);
                Subtask subtask = Subtask.builder()
                        .label(label)
                        .points(stReq.points())
                        .orderIndex(i)
                        .build();
                problem.addSubtask(subtask);
                for (TestCaseRequest tcReq : stReq.testCases()) {
                    TestCase tc = TestCase.builder()
                            .input(tcReq.input())
                            .expectedOutput(tcReq.expectedOutput())
                            .orderIndex(order++)
                            .isSample(tcReq.isSample())
                            .build();
                    problem.addTestCase(tc);
                    subtask.addTestCase(tc);
                }
            }
        } else {
            Optional.ofNullable(req.testCases()).orElse(List.of()).forEach(tcReq -> {
                TestCase tc = TestCase.builder()
                        .input(tcReq.input())
                        .expectedOutput(tcReq.expectedOutput())
                        .orderIndex(tcReq.orderIndex())
                        .isSample(tcReq.isSample())
                        .build();
                problem.addTestCase(tc);
            });
        }

        Problem saved = problemRepository.save(problem);
        return ProblemDetailResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<ProblemListResponse> list(boolean isAdmin, Pageable pageable) {
        Page<Problem> page = isAdmin
                ? problemRepository.findAll(pageable)
                : problemRepository.findAllByIsPublicTrue(pageable);
        return page.map(ProblemListResponse::from);
    }

    @Transactional(readOnly = true)
    public ProblemDetailResponse detail(Long id, boolean isAdmin) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        if (!problem.getIsPublic() && !isAdmin) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_ACCESSIBLE);
        }
        return ProblemDetailResponse.from(problem);
    }

    @Transactional
    public ProblemDetailResponse update(Long id, UpdateProblemRequest req) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        problem.update(
                req.title(),
                req.description(),
                req.inputDescription(),
                req.outputDescription(),
                req.timeLimit(),
                req.memoryLimit(),
                req.difficulty(),
                req.isPublic()
        );
        return ProblemDetailResponse.from(problem);
    }

    @Transactional
    public void delete(Long id) {
        if (!problemRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        problemRepository.deleteById(id);
    }
}
