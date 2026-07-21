package com.nplohs.market.chat.repository;

import com.nplohs.market.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // product/images/buyer/seller를 한 번에 fetch join 해서, 방 개수만큼 지연 로딩이 터지는 N+1을 방지한다.
    @Query("""
        SELECT DISTINCT r FROM ChatRoom r
        JOIN FETCH r.buyer
        JOIN FETCH r.seller
        JOIN FETCH r.product p
        LEFT JOIN FETCH p.images
        WHERE (r.buyer.id = :userId AND r.buyerLeft = false) OR (r.seller.id = :userId AND r.sellerLeft = false)
        ORDER BY COALESCE(r.lastMessageAt, r.createdAt) DESC
        """)
    List<ChatRoom> findByUserId(@Param("userId") Long userId);

    Optional<ChatRoom> findByBuyer_IdAndSeller_IdAndProduct_Id(Long buyerId, Long sellerId, Long productId);

    boolean existsByBuyer_IdOrSeller_Id(Long buyerId, Long sellerId);
}
