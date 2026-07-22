package com.nplohs.market.trade.entity;

import org.hibernate.annotations.Comment;
import com.nplohs.market.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Comment("사용자 매너 리뷰")
@Table(name = "reviews")
@Getter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("고유 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id", nullable = false)
    @Comment("거래")
    private Trade trade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    @Comment("reviewer")
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewee_id", nullable = false)
    @Comment("reviewee")
    private User reviewee;

    @Column(nullable = false)
    @Comment("score")
    private int score; // 1 ~ 5

    @Column(length = 255)
    @Comment("댓글")
    private String comment;

    @Column(nullable = false, updatable = false)
    @Comment("생성 일시")
    private LocalDateTime createdAt;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Comment("숨김 여부")
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
