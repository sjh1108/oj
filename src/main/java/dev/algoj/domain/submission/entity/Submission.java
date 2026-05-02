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
        ACCEPTED, WRONG_ANSWER, TIME_LIMIT, MEMORY_LIMIT,
        RUNTIME_ERROR, COMPILE_ERROR, SYSTEM_ERROR
    }

    @Builder
    private Submission(User user,
                       Problem problem,
                       String sourceCode,
                       Language language,
                       Status status) {
        this.user = user;
        this.problem = problem;
        this.sourceCode = sourceCode;
        this.language = language;
        this.status = status;
    }

    public void updateResult(Status status, Integer runtime, Integer memory, String errorMessage) {
        this.status = status;
        this.runtime = runtime;
        this.memory = memory;
        this.errorMessage = errorMessage;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
