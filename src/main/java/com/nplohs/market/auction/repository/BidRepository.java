package com.nplohs.market.auction.repository;

import com.nplohs.market.auction.entity.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {
    List<Bid> findByAuction_IdOrderByCreatedAtDesc(Long auctionId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Bid b WHERE b.auction.id = :auctionId")
    void deleteAllByAuctionId(@org.springframework.data.repository.query.Param("auctionId") Long auctionId);
    
    boolean existsByBidder_Id(Long bidderId);
    Page<Bid> findByAuction_IdOrderByCreatedAtDesc(Long auctionId, Pageable pageable);
    Optional<Bid> findFirstByAuction_IdOrderByAmountDesc(Long auctionId);
    Page<Bid> findByBidder_Id(Long bidderId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT b FROM Bid b WHERE b.bidder.id = :bidderId AND b.amount = (SELECT MAX(b2.amount) FROM Bid b2 WHERE b2.bidder.id = :bidderId AND b2.auction.id = b.auction.id) ORDER BY b.createdAt DESC")
    Page<Bid> findHighestBidsByBidderId(@org.springframework.data.repository.query.Param("bidderId") Long bidderId, Pageable pageable);
}
