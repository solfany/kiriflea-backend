package com.nplohs.market.product.dto;

import com.nplohs.market.auction.entity.Auction;
import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.entity.ProductType;
import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
public class ProductResponse {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Long         id;
    private final String       title;
    private final String       description;
    private final Long         price;
    private final String       category;
    private final String       status;
    private final String       type;
    @JsonProperty("isAuction")
    private final boolean      isAuction;
    private final int          viewCount;
    private final int          wishCount;
    private final String       createdAt;
    private final List<String> imageUrls;
    @JsonProperty("isHidden")
    private final boolean      isHidden;
    @JsonProperty("isDeleted")
    private final boolean      isDeleted;
    private final SellerInfo   seller;
    // 경매 전용 필드 (isAuction=true 일 때만 값 존재)
    private final Long         currentBid;
    private final String       auctionEndAt;
    private final Integer      bidCount;
    private final String       buyerNickname;
    private final Long         buyerId;
    private final boolean      hasTrade;

    public ProductResponse(Product p) {
        this(p, null, 0, false);
    }

    public ProductResponse(Product p, Auction auction) {
        this(p, auction, 0, false);
    }

    public ProductResponse(Product p, Auction auction, int listingCount) {
        this(p, auction, listingCount, false);
    }

    public ProductResponse(Product p, Auction auction, int listingCount, boolean hasTrade) {
        this.id          = p.getId();
        this.title       = p.getTitle();
        this.description = p.getDescription();
        this.price       = p.getPrice();
        this.category    = p.getCategory().name();
        this.status      = p.getStatus().name();
        this.type        = p.getType().name();
        this.isAuction   = p.getType() == ProductType.AUCTION;
        this.viewCount   = p.getViewCount();
        this.wishCount   = p.getWishCount();
        this.createdAt   = p.getCreatedAt().format(FMT);
        this.imageUrls   = p.getImages().stream().map(img -> img.getImageUrl()).toList();
        this.isHidden    = p.isHidden();
        this.isDeleted   = p.isDeleted();
        this.seller      = new SellerInfo(
                p.getSeller().getId(),
                p.getSeller().getNickname(),
                p.getSeller().getProfileImage(),
                p.getSeller().getMannerScore(),
                listingCount
        );
        if (auction != null) {
            this.currentBid   = auction.getCurrentPrice().longValue();
            this.auctionEndAt = auction.getEndAt().format(FMT);
            this.bidCount     = auction.getBidCount();
            this.buyerNickname = auction.getWinner() != null ? auction.getWinner().getNickname() : null;
            this.buyerId       = auction.getWinner() != null ? auction.getWinner().getId() : null;
        } else {
            this.currentBid   = null;
            this.auctionEndAt = null;
            this.bidCount     = null;
            this.buyerNickname = null;
            this.buyerId       = null;
        }
        this.hasTrade = hasTrade;
    }

    @Getter
    public static class SellerInfo {
        private final Long   id;
        private final String nickname;
        private final String profileImage;
        private final double mannerScore;
        private final int    listingCount;
        public SellerInfo(Long id, String nickname, String profileImage, double mannerScore, int listingCount) {
            this.id           = id;
            this.nickname     = nickname;
            this.profileImage = profileImage;
            this.mannerScore  = mannerScore;
            this.listingCount = listingCount;
        }
    }
}
