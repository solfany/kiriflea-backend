package com.nplohs.market.trade.repository;

import com.nplohs.market.trade.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    Optional<Trade> findByProduct_Id(Long productId);
    java.util.List<Trade> findByBuyer_IdOrderByCreatedAtDesc(Long buyerId);

    boolean existsBySeller_IdOrBuyer_Id(Long sellerId, Long buyerId);
    java.util.List<Trade> findByProduct_IdIn(List<Long> productIds);

    @Query("SELECT t.product.id FROM Trade t WHERE t.product.id IN :productIds")
    List<Long> findProductIdsWithTrade(@Param("productIds") List<Long> productIds);
}
