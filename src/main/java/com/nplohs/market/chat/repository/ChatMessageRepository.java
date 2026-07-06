package com.nplohs.market.chat.repository;

import com.nplohs.market.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByRoom_IdOrderByCreatedAtAsc(Long roomId);
    org.springframework.data.domain.Page<ChatMessage> findByRoom_IdOrderByCreatedAtDesc(Long roomId, org.springframework.data.domain.Pageable pageable);
    long countByRoom_IdAndSender_IdNotAndReadAtIsNull(Long roomId, Long senderId);

    @Modifying
    @Query("DELETE FROM ChatMessage c WHERE c.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") Long roomId);
}
