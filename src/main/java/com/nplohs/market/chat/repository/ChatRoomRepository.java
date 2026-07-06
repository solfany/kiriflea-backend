package com.nplohs.market.chat.repository;

import com.nplohs.market.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("SELECT r FROM ChatRoom r WHERE (r.buyer.id = :userId AND r.buyerLeft = false) OR (r.seller.id = :userId AND r.sellerLeft = false) ORDER BY COALESCE(r.lastMessageAt, r.createdAt) DESC")
    List<ChatRoom> findByUserId(@Param("userId") Long userId);

    Optional<ChatRoom> findByBuyer_IdAndSeller_IdAndProduct_Id(Long buyerId, Long sellerId, Long productId);
}
