package com.nplohs.market.auction.dto;

import com.nplohs.market.auction.entity.Auction;
import com.nplohs.market.auction.entity.Bid;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class AuctionDto {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Getter
    @Builder
    public static class BidResponse {
        private Long   id;
        private Bidder bidder;
        private long   amount;
        private String createdAt;

        public static BidResponse from(Bid bid) {
            return BidResponse.builder()
                .id(bid.getId())
                .bidder(new Bidder(bid.getBidder().getId(), bid.getBidder().getNickname()))
                .amount(bid.getAmount().longValue())
                .createdAt(bid.getCreatedAt().format(FMT))
                .build();
        }

        @Getter
        @lombok.AllArgsConstructor
        public static class Bidder {
            private Long   id;
            private String nickname;
        }
    }

    @Getter
    @Builder
    public static class AuctionUpdateMessage {
        private Long productId;
        private long currentBid;
        private int bidCount;
        private String lastBidderNickname;
        private long remainingMs;
        private String status;
        private String message;
    }

    @Getter
    @Builder
    public static class MyBidResponse {
        private Long id;
        private long amount;
        private String createdAt;
        private long currentHighestBid;
        @com.fasterxml.jackson.annotation.JsonProperty("isWinning")
        private boolean isWinning;
        private MyBidProduct product;

        @Getter
        @Builder
        public static class MyBidProduct {
            private Long id;
            private String title;
            private String thumbnailUrl;
            private String status;
            private String auctionEndAt;
            private boolean isDeleted;
        }
    }
}
