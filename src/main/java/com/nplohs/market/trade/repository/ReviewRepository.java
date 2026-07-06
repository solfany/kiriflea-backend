package com.nplohs.market.trade.repository;

import com.nplohs.market.trade.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByTrade_IdAndReviewer_Id(Long tradeId, Long reviewerId);
    List<Review> findByReviewee_IdOrderByCreatedAtDesc(Long revieweeId);
}
