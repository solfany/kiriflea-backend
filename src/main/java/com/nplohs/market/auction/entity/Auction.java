package com.nplohs.market.auction.entity;

import com.nplohs.market.auth.entity.User;
import com.nplohs.market.product.entity.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@org.hibernate.annotations.Comment("경매 정보")
@Table(name = "auctions")
@Getter
@NoArgsConstructor
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", unique = true, nullable = false)
    private Product product;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal startPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal currentPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal minBidIncrement;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status = AuctionStatus.ACTIVE;

    @Column(nullable = false)
    private int bidCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Auction(Product product, BigDecimal startPrice, BigDecimal minBidIncrement, LocalDateTime endAt) {
        this.product         = product;
        this.startPrice      = startPrice;
        this.currentPrice    = startPrice;
        this.minBidIncrement = minBidIncrement;
        this.endAt           = endAt;
    }

    public void updateCurrentPrice(BigDecimal newPrice, User bidder) {
        this.currentPrice = newPrice;
        this.bidCount++;
    }

    public void close(User winner) {
        this.status = AuctionStatus.CLOSED;
        this.winner = winner;
    }

    public void cancel() {
        this.status = AuctionStatus.CANCELLED;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public void extendTime(LocalDateTime newEndAt) {
        this.endAt = newEndAt;
    }

    public void reopen(LocalDateTime newEndAt) {
        this.status = AuctionStatus.ACTIVE;
        this.winner = null;
        this.endAt = newEndAt;
        this.currentPrice = this.startPrice;
        this.bidCount = 0;
    }
}
