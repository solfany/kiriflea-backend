package com.nplohs.market.auction.entity;

import org.hibernate.annotations.Comment;
import com.nplohs.market.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Comment("경매 입찰 내역")
@Table(name = "bids",
    indexes = {
        @Index(columnList = "auction_id, createdAt DESC"),
    }
)
@Getter
@NoArgsConstructor
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("고유 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_id", nullable = false)
    @Comment("경매")
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_id", nullable = false)
    @Comment("입찰자")
    private User bidder;

    @Column(nullable = false, precision = 15, scale = 2)
    @Comment("amount")
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    @Comment("생성 일시")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Bid(Auction auction, User bidder, BigDecimal amount) {
        this.auction = auction;
        this.bidder  = bidder;
        this.amount  = amount;
    }
}
