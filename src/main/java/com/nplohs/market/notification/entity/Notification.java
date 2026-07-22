package com.nplohs.market.notification.entity;

import org.hibernate.annotations.Comment;
import com.nplohs.market.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Comment("사용자 알림 내역")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("고유 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @Comment("사용자")
    private User user;

    @Enumerated(EnumType.STRING)
    @Comment("타입")
    private NotificationType type;

    @Comment("메시지")
    private String message;
    @Comment("linkUrl")
    private String linkUrl;
    @Comment("읽음 여부")
    private boolean isRead;

    @Column(updatable = false)
    @Comment("생성 일시")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Notification(User user, NotificationType type, String message, String linkUrl) {
        this.user = user;
        this.type = type;
        this.message = message;
        this.linkUrl = linkUrl;
        this.isRead = false;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
