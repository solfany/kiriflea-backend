package com.nplohs.market.chat.dto;

import com.nplohs.market.chat.entity.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor

public class ChatMessageDto {
    private Long   id;
    private Long   roomId;
    private Map<String, Object> sender;
    private String content;
    private String type;
    private boolean isRead;
    private String createdAt;

    public ChatMessageDto(ChatMessage msg) {
        this.id      = msg.getId();
        this.roomId  = msg.getRoom().getId();
        Map<String, Object> senderMap = new java.util.HashMap<>();
        senderMap.put("id", msg.getSender().getId());
        senderMap.put("nickname", msg.getSender().getNickname());
        senderMap.put("profileImage", msg.getSender().getProfileImage());
        this.sender  = senderMap;
        this.content   = msg.getContent();
        this.type      = msg.getType();
        this.isRead    = msg.getReadAt() != null;
        this.createdAt = msg.getCreatedAt().toString();
    }

    // STOMP 페이로드 수신용: 클라이언트가 보낼 때는 content만 있으면 됨
    public String getContent() { return content; }
}
