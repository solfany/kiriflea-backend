package com.nplohs.market.auction.repository;

import com.nplohs.market.auction.entity.Auction;
import com.nplohs.market.auction.entity.AuctionStatus;
import com.nplohs.market.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {
    Optional<Auction> findByProduct_Id(Long productId);
    List<Auction> findByStatus(AuctionStatus status);
    List<Auction> findByEndAtBeforeAndStatus(LocalDateTime time, AuctionStatus status);
    List<Auction> findByProductIn(List<Product> products);
}
