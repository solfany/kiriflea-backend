package com.nplohs.market.chat.dto;

import com.nplohs.market.chat.entity.ChatRoom;
import lombok.Getter;

import java.util.Map;

@Getter
public class ChatRoomResponse {
    private final long   id;
    private final Map<String, Object> product;
    private final Map<String, Object> partner;
    private final String lastMessage;
    private final String lastMessageAt;
    private final int    unreadCount;

    public ChatRoomResponse(ChatRoom room, Long myUserId, int unreadCount, boolean hasTrade) {
        this.id = room.getId();

        String thumb = room.getProduct().getImages().isEmpty()
                ? "" : room.getProduct().getImages().get(0).getImageUrl();
        boolean isBuyer = myUserId.equals(room.getBuyer().getId());
        boolean isSeller = myUserId.equals(room.getSeller().getId());
        var partner = isBuyer ? room.getSeller() : room.getBuyer();
        
        this.product = Map.of(
                "id",           room.getProduct().getId(),
                "title",        room.getProduct().getTitle(),
                "thumbnailUrl", thumb,
                "isSeller",     isSeller,
                "price",        room.getProduct().getPrice(),
                "status",       room.getProduct().getStatus().name(),
                "isDeleted",    room.getProduct().isDeleted(),
                "hasTrade",     hasTrade
        );

        Map<String, Object> partnerMap = new java.util.HashMap<>();
        partnerMap.put("id", partner.getId());
        partnerMap.put("nickname", partner.getNickname());
        partnerMap.put("profileImage", partner.getProfileImage());
        partnerMap.put("mannerScore", partner.getMannerScore());
        this.partner = partnerMap;

        this.lastMessage   = room.getLastMessage();
        this.lastMessageAt = room.getLastMessageAt() != null
                ? room.getLastMessageAt().toString() : null;
        this.unreadCount   = unreadCount;
    }
}
