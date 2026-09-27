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

import java.time.LocalDateTime;

// 디스코드로 보낸 선정 문제 알림. "몇 년 몇 월 몇 주차" 공지였는지 남겨, 다음 공지의
// 주차 기본값(그 달 최대 주차 + 1)을 계산하는 근거로 쓴다.
@Entity
@Table(name = "problem_announcements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announce_year", nullable = false)
    private int year;

    @Column(name = "announce_month", nullable = false)
    private int month;

    @Column(nullable = false)
    private int weekOfMonth;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private ProblemAnnouncement(int year, int month, int weekOfMonth, String content) {
        this.year = year;
        this.month = month;
        this.weekOfMonth = weekOfMonth;
        this.content = content;
    }

    public static ProblemAnnouncement of(int year, int month, int weekOfMonth, String content) {
        return new ProblemAnnouncement(year, month, weekOfMonth, content);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
