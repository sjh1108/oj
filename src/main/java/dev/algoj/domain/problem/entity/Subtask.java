package dev.algoj.domain.problem.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * A scored group of test cases (IOI-style). A submission earns a subtask's
 * {@code points} only when it passes every test case in the subtask.
 * Problems without subtasks are graded as a single implicit group.
 */
@Entity
@Table(name = "subtasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subtask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private Integer points;

    @Column(nullable = false)
    private Integer orderIndex;

    @OneToMany(mappedBy = "subtask")
    @OrderBy("orderIndex ASC")
    private List<TestCase> testCases = new ArrayList<>();

    @Builder
    private Subtask(Problem problem, String label, Integer points, Integer orderIndex) {
        this.problem = problem;
        this.label = label;
        this.points = points;
        this.orderIndex = orderIndex;
    }

    void assignProblem(Problem problem) {
        this.problem = problem;
    }

    public void addTestCase(TestCase testCase) {
        this.testCases.add(testCase);
        testCase.assignSubtask(this);
    }
}
