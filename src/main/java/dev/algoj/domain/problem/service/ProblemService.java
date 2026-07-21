package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.dto.*;
import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.Subtask;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.problem.repository.ProblemSpecs;
import dev.algoj.domain.problem.repository.TestCaseRepository;
import dev.algoj.domain.submission.repository.SubmissionRepository;
import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.repository.UserRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import dev.algoj.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;

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
                .tags(normalizeTags(req.tags()))
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
        // Just-built entity: sample cases are already in memory, no extra query.
        List<TestCase> samples = saved.getActiveTestCases().stream()
                .filter(TestCase::getIsSample)
                .toList();
        return ProblemDetailResponse.from(saved, samples);
    }

    @Transactional(readOnly = true)
    public Page<ProblemListResponse> list(ProblemSearchCondition cond,
                                          Long userId,
                                          boolean isAdmin,
                                          Pageable pageable) {
        List<Specification<Problem>> specs = new ArrayList<>();
        specs.add(ProblemSpecs.visibleTo(isAdmin));
        specs.add(ProblemSpecs.titleContains(cond.keyword()));
        specs.add(ProblemSpecs.hasDifficulty(cond.difficulty()));
        specs.add(ProblemSpecs.hasTag(cond.tag()));
        specs.add(switch (cond.solvedOrAll()) {
            case ALL -> null;
            case SOLVED -> ProblemSpecs.solvedBy(userId);
            case ATTEMPTED -> ProblemSpecs.attemptedBy(userId);
            case UNSOLVED -> ProblemSpecs.notSolvedBy(userId);
        });
        specs.removeIf(Objects::isNull);

        Page<Problem> page = problemRepository.findAll(Specification.allOf(specs), pageable);

        Set<Long> solvedIds = new HashSet<>(submissionRepository.findDistinctSolvedProblemIdsByUserId(userId));
        Set<Long> submittedIds = new HashSet<>(submissionRepository.findDistinctSubmittedProblemIdsByUserId(userId));
        return page.map(p -> ProblemListResponse.from(
                p,
                solvedIds.contains(p.getId()),
                !solvedIds.contains(p.getId()) && submittedIds.contains(p.getId())));
    }

    @Transactional(readOnly = true)
    public List<String> listTags(boolean isAdmin) {
        return problemRepository.findAllTags(isAdmin);
    }

    @Transactional(readOnly = true)
    public ProblemDetailResponse detail(Long id, boolean isAdmin) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        if (!problem.getIsPublic() && !isAdmin) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_ACCESSIBLE);
        }
        List<TestCase> samples = testCaseRepository
                .findByProblemIdAndIsSampleTrueAndIsDraftFalseOrderByOrderIndexAsc(id);
        return ProblemDetailResponse.from(problem, samples);
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
                normalizeTags(req.tags()),
                req.isPublic()
        );
        List<TestCase> samples = testCaseRepository
                .findByProblemIdAndIsSampleTrueAndIsDraftFalseOrderByOrderIndexAsc(id);
        return ProblemDetailResponse.from(problem, samples);
    }

    /** Trim, drop blanks, dedupe (case-insensitively) while keeping input order. */
    static Set<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        Set<String> seen = new HashSet<>();
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) continue;
            String t = tag.trim();
            if (t.isEmpty()) continue;
            if (seen.add(t.toLowerCase())) {
                normalized.add(t);
            }
        }
        return normalized;
    }

    @Transactional
    public void delete(Long id) {
        if (!problemRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        problemRepository.deleteById(id);
    }
}
