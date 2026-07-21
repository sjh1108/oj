package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.TestCase;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record ProblemDetailResponse(
        Long id,
        String title,
        String description,
        String inputDescription,
        String outputDescription,
        Integer timeLimit,
        Integer memoryLimit,
        Problem.Difficulty difficulty,
        List<String> tags,
        String authorUsername,
        Boolean isPublic,
        List<TestCaseResponse> sampleTestCases,
        List<SubtaskInfoResponse> subtasks,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * @param sampleTestCases only the problem's sample cases — fetched separately
     *   so a detail view never loads the (potentially multi-MB) hidden test data.
     */
    public static ProblemDetailResponse from(Problem p, List<TestCase> sampleTestCases) {
        List<TestCaseResponse> samples = sampleTestCases.stream()
                .sorted(Comparator.comparing(TestCase::getOrderIndex))
                .map(TestCaseResponse::from)
                .toList();
        List<SubtaskInfoResponse> subtasks = p.getSubtasks().stream()
                .sorted(Comparator.comparing(dev.algoj.domain.problem.entity.Subtask::getOrderIndex))
                .map(SubtaskInfoResponse::from)
                .toList();
        return new ProblemDetailResponse(
                p.getId(),
                p.getTitle(),
                p.getDescription(),
                p.getInputDescription(),
                p.getOutputDescription(),
                p.getTimeLimit(),
                p.getMemoryLimit(),
                p.getDifficulty(),
                p.getTags().stream().sorted().toList(),
                p.getAuthor() != null ? p.getAuthor().getUsername() : null,
                p.getIsPublic(),
                samples,
                subtasks,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
