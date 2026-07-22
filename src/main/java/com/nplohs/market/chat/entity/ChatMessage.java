package com.nplohs.market.chat.entity;

import com.nplohs.market.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Comment("채팅 메시지")
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("고유 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    @Comment("room")
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    @Comment("발신자")
    private User sender;

    @Column(columnDefinition = "TEXT", nullable = false)
    @Comment("내용")
    private String content;

    @Column(nullable = false)
    @Comment("타입")
    private String type = "TEXT";

    @Comment("읽은 일시")
    private LocalDateTime readAt;

    @Column(nullable = false, updatable = false)
    @Comment("생성 일시")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public ChatMessage(ChatRoom room, User sender, String content) {
        this.room    = room;
        this.sender  = sender;
        this.content = content;
        this.type    = "TEXT";
    }

    public ChatMessage(ChatRoom room, User sender, String content, String type) {
        this.room    = room;
        this.sender  = sender;
        this.content = content;
        this.type    = type;
    }

    public void markRead() { this.readAt = LocalDateTime.now(); }
}
