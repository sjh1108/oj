package dev.algoj.domain.problem.entity;

import dev.algoj.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 문제를 풀면서 남기는 개인 메모. (유저, 문제) 당 한 행이고, 본인만 읽고 쓴다.
// 내용이 비면 서비스가 행을 지우므로 content는 항상 비어있지 않다.
@Entity
@Table(
        name = "problem_notes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_problem_notes_user_problem",
                columnNames = {"user_id", "problem_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemNote {

    // 메모 한 편의 상한. 풀이 메모는 짧다 — 이보다 길어지면 그건 에디토리얼이다.
    public static final int MAX_LENGTH = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private ProblemNote(User user, Problem problem, String content) {
        this.user = user;
        this.problem = problem;
        this.content = content;
    }

    public static ProblemNote of(User user, Problem problem, String content) {
        return new ProblemNote(user, problem, content);
    }

    public void updateContent(String content) {
        this.content = content;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
