package com.nplohs.market.trade.repository;

import com.nplohs.market.trade.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    Optional<Trade> findByProduct_Id(Long productId);
    java.util.List<Trade> findByBuyer_IdOrderByCreatedAtDesc(Long buyerId);
}
