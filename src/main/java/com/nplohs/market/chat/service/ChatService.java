package com.nplohs.market.chat.service;

import com.nplohs.market.auth.entity.User;
import com.nplohs.market.auth.repository.UserRepository;
import com.nplohs.market.chat.dto.ChatMessageDto;
import com.nplohs.market.chat.dto.ChatRoomResponse;
import com.nplohs.market.chat.entity.ChatMessage;
import com.nplohs.market.chat.entity.ChatRoom;
import com.nplohs.market.chat.repository.ChatMessageRepository;
import com.nplohs.market.chat.repository.ChatRoomRepository;
import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.repository.ProductRepository;
import com.nplohs.market.trade.repository.TradeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository    chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository        userRepository;
    private final ProductRepository     productRepository;
    private final TradeRepository       tradeRepository;

    // ── 채팅방 조회 또는 생성 ─────────────────────────────────────
    @Transactional
    public ChatRoomResponse getOrCreateRoom(String buyerEmail, Long sellerId, Long productId) {
        User    buyer   = findUser(buyerEmail);
        User    seller  = userRepository.findById(sellerId)
                .orElseThrow(() -> new EntityNotFoundException("Seller not found: " + sellerId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

        ChatRoom room = chatRoomRepository
                .findByBuyer_IdAndSeller_IdAndProduct_Id(buyer.getId(), sellerId, productId)
                .orElseGet(() -> chatRoomRepository.save(new ChatRoom(buyer, seller, product)));

        int unreadCount = (int) chatMessageRepository.countByRoom_IdAndSender_IdNotAndReadAtIsNull(room.getId(), buyer.getId());
        boolean hasTrade = tradeRepository.findByProduct_Id(productId).isPresent();
        return new ChatRoomResponse(room, buyer.getId(), unreadCount, hasTrade);
    }

    @Transactional
    public ChatRoomResponse createRoomAsSeller(String sellerEmail, Long buyerId, Long productId) {
        User seller = findUser(sellerEmail);
        User buyer  = userRepository.findById(buyerId)
                .orElseThrow(() -> new EntityNotFoundException("Buyer not found: " + buyerId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

        if (!product.getSeller().getId().equals(seller.getId())) {
            throw new IllegalArgumentException("판매자 본인만 이 기능을 사용할 수 있습니다.");
        }

        ChatRoom room = chatRoomRepository
                .findByBuyer_IdAndSeller_IdAndProduct_Id(buyerId, seller.getId(), productId)
                .orElseGet(() -> chatRoomRepository.save(new ChatRoom(buyer, seller, product)));

        int unreadCount = (int) chatMessageRepository.countByRoom_IdAndSender_IdNotAndReadAtIsNull(room.getId(), seller.getId());
        boolean hasTrade = tradeRepository.findByProduct_Id(productId).isPresent();
        return new ChatRoomResponse(room, seller.getId(), unreadCount, hasTrade);
    }

    // ── 채팅방 목록 ──────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getRooms(String userEmail) {
        User user = findUser(userEmail);
        return chatRoomRepository.findByUserId(user.getId())
                .stream()
                .map(room -> {
                    int unreadCount = (int) chatMessageRepository.countByRoom_IdAndSender_IdNotAndReadAtIsNull(room.getId(), user.getId());
                    boolean hasTrade = tradeRepository.findByProduct_Id(room.getProduct().getId()).isPresent();
                    return new ChatRoomResponse(room, user.getId(), unreadCount, hasTrade);
                })
                .toList();
    }

    // ── 전체 안 읽은 메시지 수 ────────────────────────────────────────
    @Transactional(readOnly = true)
    public int getTotalUnreadCount(String userEmail) {
        User user = findUser(userEmail);
        return chatRoomRepository.findByUserId(user.getId())
                .stream()
                .mapToInt(room -> (int) chatMessageRepository.countByRoom_IdAndSender_IdNotAndReadAtIsNull(room.getId(), user.getId()))
                .sum();
    }

    // ── 채팅방 단건 조회 ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public ChatRoomResponse getRoom(String userEmail, Long roomId) {
        User user = findUser(userEmail);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found: " + roomId));
        int unreadCount = (int) chatMessageRepository.countByRoom_IdAndSender_IdNotAndReadAtIsNull(room.getId(), user.getId());
        boolean hasTrade = tradeRepository.findByProduct_Id(room.getProduct().getId()).isPresent();
        return new ChatRoomResponse(room, user.getId(), unreadCount, hasTrade);
    }

    // ── 메시지 목록 ──────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(String userEmail, Long roomId) {
        findUser(userEmail); // auth check
        return chatMessageRepository.findByRoom_IdOrderByCreatedAtAsc(roomId)
                .stream().map(ChatMessageDto::new).toList();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ChatMessageDto> getMessages(String userEmail, Long roomId, org.springframework.data.domain.Pageable pageable) {
        findUser(userEmail); // auth check
        return chatMessageRepository.findByRoom_IdOrderByCreatedAtDesc(roomId, pageable)
                .map(ChatMessageDto::new);
    }

    // ── STOMP 메시지 저장 + 반환 (MessageBroker로 브로드캐스트) ──
    @Transactional
    public ChatMessageDto saveMessage(String senderEmail, Long roomId, String content, String type) {
        User     sender = findUser(senderEmail);
        ChatRoom room   = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found: " + roomId));

        // 발신자가 방 참여자인지 확인
        if (!room.getBuyer().getId().equals(sender.getId())
                && !room.getSeller().getId().equals(sender.getId())) {
            throw new IllegalStateException("채팅방 접근 권한이 없습니다.");
        }

        // 새 메시지가 오면 방을 나갔던 상대방의 방을 다시 살림
        room.setBuyerLeft(false);
        room.setSellerLeft(false);

        ChatMessage msg = chatMessageRepository.save(new ChatMessage(room, sender, content, type));
        room.updateLastMessage("IMAGE".equals(type) ? "사진을 보냈습니다." : content);
        chatRoomRepository.save(room);

        return new ChatMessageDto(msg);
    }

    // ── 읽음 처리 ────────────────────────────────────────────────
    @Transactional
    public void markRead(String userEmail, Long roomId) {
        User user = findUser(userEmail);
        chatMessageRepository.findByRoom_IdOrderByCreatedAtAsc(roomId).stream()
                .filter(m -> !m.getSender().getId().equals(user.getId()) && m.getReadAt() == null)
                .forEach(ChatMessage::markRead);
    }

    @Transactional(readOnly = true)
    public Long getPartnerId(Long roomId, String userEmail) {
        User user = findUser(userEmail);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found"));
        return room.getSeller().getId().equals(user.getId()) 
                ? room.getBuyer().getId() 
                : room.getSeller().getId();
    }

    // ── 채팅방 삭제 ────────────────────────────────────────────────
    @Transactional
    public void deleteRoom(String userEmail, Long roomId) {
        User user = findUser(userEmail);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found: " + roomId));

        // 권한 확인 및 Soft Delete 플래그 설정
        if (room.getBuyer().getId().equals(user.getId())) {
            room.setBuyerLeft(true);
        } else if (room.getSeller().getId().equals(user.getId())) {
            room.setSellerLeft(true);
        } else {
            throw new IllegalStateException("채팅방 삭제 권한이 없습니다.");
        }

        // 양쪽 모두 방을 나갔다면 실제 데이터 영구 삭제
        if (room.isBuyerLeft() && room.isSellerLeft()) {
            chatMessageRepository.deleteByRoomId(roomId);
            chatRoomRepository.delete(room);
        } else {
            chatRoomRepository.save(room);
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
    }
}
