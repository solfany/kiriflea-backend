package com.nplohs.market.auction.entity;

import org.hibernate.annotations.Comment;
import com.nplohs.market.user.entity.User;
import com.nplohs.market.product.entity.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Comment("경매 정보")
@Table(name = "auctions")
@Getter
@NoArgsConstructor
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("고유 ID")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", unique = true, nullable = false)
    @Comment("상품")
    private Product product;

    @Column(nullable = false, precision = 15, scale = 2)
    @Comment("시작가")
    private BigDecimal startPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    @Comment("현재가")
    private BigDecimal currentPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    @Comment("minBidIncrement")
    private BigDecimal minBidIncrement;

    @Column(nullable = false)
    @Comment("endAt")
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Comment("상태")
    private AuctionStatus status = AuctionStatus.ACTIVE;

    @Column(nullable = false)
    @Comment("입찰 횟수")
    private int bidCount = 0;

    // 동시에 여러 입찰이 들어올 때 낙관적 락으로 lost-update를 막는다.
    // (예: 두 입찰이 동시에 currentPrice=1000을 읽고 각각 1200/1100으로 갱신하면,
    //  버전 체크 없이는 나중에 커밋되는 쪽이 이겨서 실제 최고가보다 낮은 값으로 덮어써질 수 있다)
    @Version
    @Comment("version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    @Comment("winner")
    private User winner;

    @Column(nullable = false, updatable = false)
    @Comment("생성 일시")
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

    public void extendEndAt(long minutes) {
        this.endAt = this.endAt.plusMinutes(minutes);
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

    public void rollbackToNextBid(BigDecimal nextPrice, int newBidCount, LocalDateTime newEndAt) {
        this.status = AuctionStatus.ACTIVE;
        this.winner = null;
        this.endAt = newEndAt;
        this.currentPrice = nextPrice;
        this.bidCount = newBidCount;
    }
}
