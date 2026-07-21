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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

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

        if (!product.getSeller().getId().equals(sellerId)) {
            throw new IllegalArgumentException("해당 상품의 판매자가 아닙니다.");
        }

        ChatRoom room = chatRoomRepository
                .findByBuyer_IdAndSeller_IdAndProduct_Id(buyer.getId(), sellerId, productId)
                .orElseGet(() -> chatRoomRepository.save(new ChatRoom(buyer, seller, product)));

        LocalDateTime leftAt = room.getBuyer().getId().equals(buyer.getId()) ? room.getBuyerLeftAt() : room.getSellerLeftAt();
        int unreadCount = (int) chatMessageRepository.countUnreadSince(room.getId(), buyer.getId(), leftAt);
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

        LocalDateTime leftAt = room.getBuyer().getId().equals(seller.getId()) ? room.getBuyerLeftAt() : room.getSellerLeftAt();
        int unreadCount = (int) chatMessageRepository.countUnreadSince(room.getId(), seller.getId(), leftAt);
        boolean hasTrade = tradeRepository.findByProduct_Id(productId).isPresent();
        return new ChatRoomResponse(room, seller.getId(), unreadCount, hasTrade);
    }

    // ── 채팅방 목록 ──────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getRooms(String userEmail) {
        User user = findUser(userEmail);
        List<ChatRoom> rooms = chatRoomRepository.findByUserId(user.getId());
        Map<Long, Integer> unreadCounts = computeUnreadCounts(rooms, user.getId());
        Set<Long> tradedProductIds = productIdsWithTrade(rooms);

        return rooms.stream()
                .map(room -> new ChatRoomResponse(
                        room, user.getId(),
                        unreadCounts.getOrDefault(room.getId(), 0),
                        tradedProductIds.contains(room.getProduct().getId())))
                .toList();
    }

    // ── 전체 안 읽은 메시지 수 ────────────────────────────────────────
    @Transactional(readOnly = true)
    public int getTotalUnreadCount(String userEmail) {
        User user = findUser(userEmail);
        List<ChatRoom> rooms = chatRoomRepository.findByUserId(user.getId());
        return computeUnreadCounts(rooms, user.getId()).values().stream().mapToInt(Integer::intValue).sum();
    }

    // 방 개수만큼 countUnreadSince/findByProduct_Id를 반복 호출하던 N+1을 없애기 위해,
    // 안 읽은 메시지와 거래 존재 여부를 방 목록 단위로 한 번씩만 조회한다.
    private Map<Long, Integer> computeUnreadCounts(List<ChatRoom> rooms, Long userId) {
        if (rooms.isEmpty()) return Map.of();

        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<Long, LocalDateTime> leftAtByRoom = new HashMap<>();
        for (ChatRoom room : rooms) {
            LocalDateTime leftAt = room.getBuyer().getId().equals(userId) ? room.getBuyerLeftAt() : room.getSellerLeftAt();
            leftAtByRoom.put(room.getId(), leftAt);
        }

        Map<Long, Integer> counts = new HashMap<>();
        for (ChatMessageRepository.UnreadMessageView v : chatMessageRepository.findUnreadForRooms(roomIds, userId)) {
            LocalDateTime leftAt = leftAtByRoom.get(v.getRoomId());
            if (leftAt == null || v.getCreatedAt().isAfter(leftAt)) {
                counts.merge(v.getRoomId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private Set<Long> productIdsWithTrade(List<ChatRoom> rooms) {
        if (rooms.isEmpty()) return Set.of();
        List<Long> productIds = rooms.stream().map(r -> r.getProduct().getId()).distinct().toList();
        return new HashSet<>(tradeRepository.findProductIdsWithTrade(productIds));
    }

    // ── 채팅방 단건 조회 ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public ChatRoomResponse getRoom(String userEmail, Long roomId) {
        User user = findUser(userEmail);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found: " + roomId));
        assertParticipant(room, user);
        LocalDateTime leftAt = room.getBuyer().getId().equals(user.getId()) ? room.getBuyerLeftAt() : room.getSellerLeftAt();
        int unreadCount = (int) chatMessageRepository.countUnreadSince(room.getId(), user.getId(), leftAt);
        boolean hasTrade = tradeRepository.findByProduct_Id(room.getProduct().getId()).isPresent();
        return new ChatRoomResponse(room, user.getId(), unreadCount, hasTrade);
    }

    // ── 메시지 목록 ──────────────────────────────────────────────
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ChatMessageDto> getMessages(String userEmail, Long roomId, org.springframework.data.domain.Pageable pageable) {
        User user = findUser(userEmail); // auth check
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found: " + roomId));
        assertParticipant(room, user);
        LocalDateTime leftAt = room.getBuyer().getId().equals(user.getId()) ? room.getBuyerLeftAt() : room.getSellerLeftAt();

        return chatMessageRepository.findMessagesSincePage(roomId, leftAt, pageable)
                .map(ChatMessageDto::new);
    }

    private static final int MAX_MESSAGE_LENGTH = 2000;

    // ── STOMP 메시지 저장 + 반환 (MessageBroker로 브로드캐스트) ──
    @Transactional
    public ChatMessageDto saveMessage(String senderEmail, Long roomId, String content, String type) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("메시지 내용을 입력해주세요.");
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("메시지는 " + MAX_MESSAGE_LENGTH + "자 이하로 작성해주세요.");
        }

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
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found: " + roomId));
        assertParticipant(room, user);
        chatMessageRepository.markReadByRoomIdAndUserId(roomId, user.getId());
    }

    @Transactional(readOnly = true)
    public Long getPartnerId(Long roomId, String userEmail) {
        User user = findUser(userEmail);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found"));
        assertParticipant(room, user);
        return room.getSeller().getId().equals(user.getId())
                ? room.getBuyer().getId()
                : room.getSeller().getId();
    }

    // ── 방 참여자 검증 ───────────────────────────────────────────
    private void assertParticipant(ChatRoom room, User user) {
        if (!room.getBuyer().getId().equals(user.getId()) && !room.getSeller().getId().equals(user.getId())) {
            throw new AccessDeniedException("채팅방 접근 권한이 없습니다.");
        }
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
