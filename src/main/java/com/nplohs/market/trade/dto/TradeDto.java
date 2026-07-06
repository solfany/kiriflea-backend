package com.nplohs.market.trade.dto;

import com.nplohs.market.trade.entity.Review;
import com.nplohs.market.trade.entity.Trade;
import lombok.Builder;
import lombok.Getter;

public class TradeDto {

    @Getter
    public static class CreateTradeRequest {
        private Long productId;
        private Long buyerId;
    }

    @Getter
    public static class CreateReviewRequest {
        private int score;
        private String comment;
    }

    @Getter
    @Builder
    public static class TradeResponse {
        private Long id;
        private Long productId;
        private String productTitle;
        private String productThumbnail;
        private Long sellerId;
        private String sellerNickname;
        private Long buyerId;
        private String buyerNickname;
        private String createdAt;

        private boolean buyerReviewed;
        private boolean sellerReviewed;

        public static TradeResponse from(Trade t, boolean buyerReviewed, boolean sellerReviewed) {
            String thumbnail = t.getProduct().getImages().isEmpty() ? null : t.getProduct().getImages().get(0).getImageUrl();
            return TradeResponse.builder()
                .id(t.getId())
                .productId(t.getProduct().getId())
                .productTitle(t.getProduct().getTitle())
                .productThumbnail(thumbnail)
                .sellerId(t.getSeller().getId())
                .sellerNickname(t.getSeller().getNickname())
                .buyerId(t.getBuyer().getId())
                .buyerNickname(t.getBuyer().getNickname())
                .createdAt(t.getCreatedAt().toString())
                .buyerReviewed(buyerReviewed)
                .sellerReviewed(sellerReviewed)
                .build();
        }
    }

    @Getter
    @Builder
    public static class ReviewResponse {
        private Long id;
        private Long tradeId;
        private String reviewerNickname;
        private int score;
        private String comment;
        private String createdAt;

        public static ReviewResponse from(Review r) {
            return ReviewResponse.builder()
                .id(r.getId())
                .tradeId(r.getTrade().getId())
                .reviewerNickname(r.getReviewer().getNickname())
                .score(r.getScore())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt().toString())
                .build();
        }
    }
}
