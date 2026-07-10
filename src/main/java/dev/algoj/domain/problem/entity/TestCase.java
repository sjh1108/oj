package dev.algoj.domain.problem.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "test_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    // Null for legacy problems graded as a single implicit group.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subtask_id")
    private Subtask subtask;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String input;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String expectedOutput;

    @Column(nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    private Boolean isSample;

    // Mid-(chunked)-upload marker — draft cases are excluded from judging and
    // sample display until the uploader finalizes them.
    @Column(nullable = false)
    private Boolean isDraft;

    @Builder
    private TestCase(Problem problem,
                     String input,
                     String expectedOutput,
                     Integer orderIndex,
                     Boolean isSample,
                     Boolean isDraft) {
        this.problem = problem;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.orderIndex = orderIndex;
        this.isSample = isSample;
        this.isDraft = isDraft != null ? isDraft : Boolean.FALSE;
    }

    void assignProblem(Problem problem) {
        this.problem = problem;
    }

    public void assignSubtask(Subtask subtask) {
        this.subtask = subtask;
    }

    public void update(String input, String expectedOutput, Integer orderIndex, Boolean isSample) {
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.orderIndex = orderIndex;
        this.isSample = isSample;
    }
}
