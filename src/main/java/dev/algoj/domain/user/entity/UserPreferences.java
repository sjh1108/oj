package dev.algoj.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 계정에 붙는 개인 보기 설정. 유저당 한 행이고 PK는 users.id를 그대로 쓴다(@MapsId).
// 행이 없으면 전부 기본값 — 회원가입 때 미리 만들지 않고, 처음 설정을 바꿀 때 생긴다.
// 설정을 추가할 때는 컬럼 + 필드 + 응답 DTO 필드를 늘리면 된다(마이그레이션 1개).
@Entity
@Table(name = "user_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferences {

    // 태그는 풀이 방향을 알려주는 스포일러라 기본은 숨김.
    public static final boolean DEFAULT_SHOW_TAGS = false;

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private boolean showTags;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private UserPreferences(User user) {
        this.user = user;
        this.showTags = DEFAULT_SHOW_TAGS;
    }

    public static UserPreferences defaultsFor(User user) {
        return new UserPreferences(user);
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

    public void updateShowTags(boolean showTags) {
        this.showTags = showTags;
    }
}
