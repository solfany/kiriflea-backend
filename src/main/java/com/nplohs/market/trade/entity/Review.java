package com.nplohs.market.trade.entity;

import com.nplohs.market.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@org.hibernate.annotations.Comment("사용자 매너 리뷰")
@Table(name = "reviews")
@Getter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @org.hibernate.annotations.Comment("고유 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id", nullable = false)
    private Trade trade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewee_id", nullable = false)
    private User reviewee;

    @Column(nullable = false)
    private int score; // 1 ~ 5

    @Column(length = 255)
    private String comment;

    @Column(nullable = false, updatable = false)
    @org.hibernate.annotations.Comment("생성 일시")

    private LocalDateTime createdAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @org.hibernate.annotations.Comment("숨김 여부")

    private boolean isHidden = false;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Review(Trade trade, User reviewer, User reviewee, int score, String comment) {
        this.trade = trade;
        this.reviewer = reviewer;
        this.reviewee = reviewee;
        this.score = score;
        this.comment = comment;
    }

    public void setHidden(boolean hidden) {
        this.isHidden = hidden;
    }
}
