package dev.algoj.domain.problem.entity;

import dev.algoj.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "problems")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String inputDescription;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String outputDescription;

    @Column(nullable = false)
    private Integer timeLimit;

    @Column(nullable = false)
    private Integer memoryLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestCase> testCases = new ArrayList<>();

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<Subtask> subtasks = new ArrayList<>();

    @Column(nullable = false)
    private Boolean isPublic;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum Difficulty {
        BRONZE, SILVER, GOLD, PLATINUM, DIAMOND
    }

    @Builder
    private Problem(String title,
                    String description,
                    String inputDescription,
                    String outputDescription,
                    Integer timeLimit,
                    Integer memoryLimit,
                    Difficulty difficulty,
                    User author,
                    Boolean isPublic) {
        this.title = title;
        this.description = description;
        this.inputDescription = inputDescription;
        this.outputDescription = outputDescription;
        this.timeLimit = timeLimit;
        this.memoryLimit = memoryLimit;
        this.difficulty = difficulty;
        this.author = author;
        this.isPublic = isPublic;
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

    public void update(String title,
                       String description,
                       String inputDescription,
                       String outputDescription,
                       Integer timeLimit,
                       Integer memoryLimit,
                       Difficulty difficulty,
                       Boolean isPublic) {
        this.title = title;
        this.description = description;
        this.inputDescription = inputDescription;
        this.outputDescription = outputDescription;
        this.timeLimit = timeLimit;
        this.memoryLimit = memoryLimit;
        this.difficulty = difficulty;
        this.isPublic = isPublic;
    }

    public void addTestCase(TestCase testCase) {
        this.testCases.add(testCase);
        testCase.assignProblem(this);
    }

    public void addSubtask(Subtask subtask) {
        this.subtasks.add(subtask);
        subtask.assignProblem(this);
    }

    public boolean removeTestCase(Long testCaseId) {
        return this.testCases.removeIf(tc -> tc.getId() != null && tc.getId().equals(testCaseId));
    }
}
