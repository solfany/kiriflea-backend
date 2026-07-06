package com.nplohs.market.chat.controller;

import com.nplohs.market.chat.dto.ChatMessageDto;
import com.nplohs.market.chat.dto.ChatRoomResponse;
import com.nplohs.market.chat.service.ChatService;
import com.nplohs.market.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService            chatService;
    private final SimpMessagingTemplate  messagingTemplate;

    // ── REST ─────────────────────────────────────────────────────

    /** GET /api/chat/rooms — 내 채팅방 목록 */
    @GetMapping("/api/chat/rooms")
    public ResponseEntity<ApiResponse<List<ChatRoomResponse>>> getRooms(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.getRooms(user.getUsername())));
    }

    /** GET /api/chat/unread-count — 전체 안 읽은 메시지 수 */
    @GetMapping("/api/chat/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getUnreadCount(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("unreadCount", chatService.getTotalUnreadCount(user.getUsername()))));
    }

    /** GET /api/chat/rooms/{roomId} — 채팅방 단건 조회 */
    @GetMapping("/api/chat/rooms/{roomId}")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> getRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.getRoom(user.getUsername(), roomId)));
    }

    /**
     * POST /api/chat/rooms
     * Body: { sellerId, productId }
     */
    @PostMapping("/api/chat/rooms")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> createOrGetRoom(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, Long> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatService.getOrCreateRoom(user.getUsername(), body.get("sellerId"), body.get("productId"))
        ));
    }

    /**
     * POST /api/chat/rooms/seller-initiate
     * Body: { buyerId, productId }
     */
    @PostMapping("/api/chat/rooms/seller-initiate")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> createRoomAsSeller(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, Long> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                chatService.createRoomAsSeller(user.getUsername(), body.get("buyerId"), body.get("productId"))
        ));
    }

    /** GET /api/chat/rooms/{roomId}/messages — 이전 메시지 */
    @GetMapping("/api/chat/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<ChatMessageDto>>> getMessages(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetails user,
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        chatService.markRead(user.getUsername(), roomId);
        return ResponseEntity.ok(ApiResponse.ok(chatService.getMessages(user.getUsername(), roomId, pageable)));
    }

    /** DELETE /api/chat/rooms/{roomId} — 채팅방 삭제 */
    @DeleteMapping("/api/chat/rooms/{roomId}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetails user) {
        chatService.deleteRoom(user.getUsername(), roomId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── WebSocket STOMP ───────────────────────────────────────────

    @MessageMapping("/chat/{roomId}")
    public void sendMessage(@DestinationVariable Long roomId,
                            @Payload ChatMessageDto payload,
                            Principal principal) {
        String type = payload.getType() != null ? payload.getType() : "TEXT";
        ChatMessageDto saved = chatService.saveMessage(principal.getName(), roomId, payload.getContent(), type);
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, saved);
        
        Long partnerId = chatService.getPartnerId(roomId, principal.getName());
        messagingTemplate.convertAndSend("/topic/user/" + partnerId + "/chats", saved);
    }

    @MessageMapping("/chat/{roomId}/read")
    public void markRead(@DestinationVariable Long roomId, Principal principal) {
        chatService.markRead(principal.getName(), roomId);
        messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/read", Map.of("type", "READ", "reader", principal.getName()));
    }
}
