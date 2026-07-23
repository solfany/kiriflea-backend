package com.nplohs.market.notification.entity;

import org.hibernate.annotations.Comment;
import com.nplohs.market.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
    @Comment("수신 사용자")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    @Comment("알림을 발생시킨 사용자 (채팅 발신자, 입찰자 등)")
    private User actor;

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

    /** actor 없는 기존 생성자 (시스템 알림 등) */
    public Notification(User user, NotificationType type, String message, String linkUrl) {
        this.user = user;
        this.type = type;
        this.message = message;
        this.linkUrl = linkUrl;
        this.isRead = false;
    }

    /** actor 있는 생성자 (채팅, 입찰, 좋아요 등 사용자 유발 알림) */
    public Notification(User user, User actor, NotificationType type, String message, String linkUrl) {
        this.user = user;
        this.actor = actor;
        this.type = type;
        this.message = message;
        this.linkUrl = linkUrl;
        this.isRead = false;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
