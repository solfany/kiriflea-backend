package com.nplohs.market.chat.repository;

import com.nplohs.market.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    @Query("SELECT c FROM ChatMessage c WHERE c.room.id = :roomId AND (:leftAt IS NULL OR c.createdAt > :leftAt) ORDER BY c.createdAt DESC")
    org.springframework.data.domain.Page<ChatMessage> findMessagesSincePage(@Param("roomId") Long roomId, @Param("leftAt") LocalDateTime leftAt, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(c) FROM ChatMessage c WHERE c.room.id = :roomId AND c.sender.id != :userId AND c.readAt IS NULL AND (:leftAt IS NULL OR c.createdAt > :leftAt)")
    long countUnreadSince(@Param("roomId") Long roomId, @Param("userId") Long userId, @Param("leftAt") LocalDateTime leftAt);

    // 방 목록 조회 시 방 개수만큼 countUnreadSince를 반복 호출하지 않도록, 안 읽은 메시지를
    // 한 번에 가져와 room별 leftAt 기준은 애플리케이션 레벨에서 계산한다(안 읽은 메시지 수는
    // 보통 적으므로 전체 엔티티 대신 필요한 필드만 가져온다).
    @Query("SELECT c.room.id AS roomId, c.createdAt AS createdAt FROM ChatMessage c " +
           "WHERE c.room.id IN :roomIds AND c.sender.id != :userId AND c.readAt IS NULL")
    List<UnreadMessageView> findUnreadForRooms(@Param("roomIds") List<Long> roomIds, @Param("userId") Long userId);

    interface UnreadMessageView {
        Long getRoomId();
        LocalDateTime getCreatedAt();
    }

    @Modifying
    @Query("DELETE FROM ChatMessage c WHERE c.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") Long roomId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ChatMessage c SET c.readAt = CURRENT_TIMESTAMP WHERE c.room.id = :roomId AND c.sender.id != :userId AND c.readAt IS NULL")
    int markReadByRoomIdAndUserId(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
