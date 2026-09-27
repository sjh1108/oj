package dev.algoj.domain.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 디스코드로 보낸 선정 문제 알림. 주차 계산의 근거라서 스터디 날짜를 기준으로 남긴다.
@Entity
@Table(name = "problem_announcements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate studyDate;

    @Column(nullable = false)
    private int weekOfMonth;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private ProblemAnnouncement(LocalDate studyDate, int weekOfMonth, String content) {
        this.studyDate = studyDate;
        this.weekOfMonth = weekOfMonth;
        this.content = content;
    }

    public static ProblemAnnouncement of(LocalDate studyDate, int weekOfMonth, String content) {
        return new ProblemAnnouncement(studyDate, weekOfMonth, content);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
