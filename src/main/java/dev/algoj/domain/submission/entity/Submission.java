package dev.algoj.domain.submission.entity;

import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private Integer runtime;

    private Integer memory;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Integer passedTestCases;

    @Column(nullable = false)
    private Integer totalTestCases;

    // Subtask scoring: earned points and the max (sum of subtask points).
    private Integer score;

    private Integer maxScore;

    // Per-subtask breakdown serialized as JSON for the detail view.
    @Lob
    @Column(columnDefinition = "TEXT")
    private String subtaskResultsJson;

    @Column(nullable = false)
    private Boolean isPublic;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Getter
    public enum Language {
        PYTHON3(71),
        CPP(54),
        JAVA(62),
        C(50),
        JAVASCRIPT(63);

        private final int judge0Id;

        Language(int judge0Id) {
            this.judge0Id = judge0Id;
        }
    }

    public enum Status {
        PENDING, JUDGING,
        ACCEPTED, PARTIAL, WRONG_ANSWER, TIME_LIMIT, MEMORY_LIMIT,
        RUNTIME_ERROR, COMPILE_ERROR, SYSTEM_ERROR
    }

    @Builder
    private Submission(User user,
                       Problem problem,
                       String sourceCode,
                       Language language,
                       Status status,
                       Integer totalTestCases) {
        this.user = user;
        this.problem = problem;
        this.sourceCode = sourceCode;
        this.language = language;
        this.status = status;
        this.passedTestCases = 0;
        this.totalTestCases = totalTestCases != null ? totalTestCases : 0;
        this.isPublic = true;
    }

    public void markJudging() {
        this.status = Status.JUDGING;
    }

    public void incrementPassed(Integer runtimeMs, Integer memoryKb) {
        this.passedTestCases++;
        if (runtimeMs != null) {
            this.runtime = (this.runtime == null) ? runtimeMs : Math.max(this.runtime, runtimeMs);
        }
        if (memoryKb != null) {
            this.memory = (this.memory == null) ? memoryKb : Math.max(this.memory, memoryKb);
        }
    }

    public void updateResult(Status status, Integer runtime, Integer memory, String errorMessage) {
        this.status = status;
        if (runtime != null) {
            this.runtime = (this.runtime == null) ? runtime : Math.max(this.runtime, runtime);
        }
        if (memory != null) {
            this.memory = (this.memory == null) ? memory : Math.max(this.memory, memory);
        }
        this.errorMessage = errorMessage;
    }

    public void updateScore(Integer score, Integer maxScore, String subtaskResultsJson) {
        this.score = score;
        this.maxScore = maxScore;
        this.subtaskResultsJson = subtaskResultsJson;
    }

    public void setVisibility(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public void resetForRejudge(int totalTestCases) {
        this.status = Status.PENDING;
        this.passedTestCases = 0;
        this.totalTestCases = totalTestCases;
        this.runtime = null;
        this.memory = null;
        this.errorMessage = null;
        this.score = null;
        this.maxScore = null;
        this.subtaskResultsJson = null;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.passedTestCases == null) this.passedTestCases = 0;
        if (this.totalTestCases == null) this.totalTestCases = 0;
        if (this.isPublic == null) this.isPublic = true;
    }
}
