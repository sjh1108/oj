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

    @Builder
    private TestCase(Problem problem,
                     String input,
                     String expectedOutput,
                     Integer orderIndex,
                     Boolean isSample) {
        this.problem = problem;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.orderIndex = orderIndex;
        this.isSample = isSample;
    }

    void assignProblem(Problem problem) {
        this.problem = problem;
    }

    public void update(String input, String expectedOutput, Integer orderIndex, Boolean isSample) {
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.orderIndex = orderIndex;
        this.isSample = isSample;
    }
}
