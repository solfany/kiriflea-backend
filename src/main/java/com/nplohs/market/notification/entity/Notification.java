package com.nplohs.market.notification.entity;

import com.nplohs.market.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@org.hibernate.annotations.Comment("사용자 알림 내역")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @org.hibernate.annotations.Comment("고유 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @org.hibernate.annotations.Comment("사용자")
    private User user;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.Comment("타입")

    private NotificationType type;

    @org.hibernate.annotations.Comment("메시지")

    private String message;
    private String linkUrl;
    @org.hibernate.annotations.Comment("읽음 여부")
    private boolean isRead;

    @Column(updatable = false)
    @org.hibernate.annotations.Comment("생성 일시")

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
