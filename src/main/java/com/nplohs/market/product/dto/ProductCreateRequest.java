package com.nplohs.market.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nplohs.market.product.entity.ProductCategory;
import com.nplohs.market.product.entity.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class ProductCreateRequest {

    @NotBlank
    @Size(max = 100, message = "제목은 100자 이하입니다.")
    private String title;

    private String description;

    private Long price;

    @NotNull
    private ProductCategory category;

    // 프론트가 isAuction(boolean) 으로 전송하는 경우 대응
    @JsonProperty("isAuction")
    private Boolean isAuction;

    // 프론트가 type 문자열로 직접 보내는 경우도 수용
    private ProductType type;

    // 프론트가 imageUrls 또는 imageIds 중 어느 쪽을 보내도 처리
    @Size(max = 10, message = "이미지는 최대 10장입니다.")
    private List<String> imageUrls;

    private List<String> imageIds; // 업로드 후 id 대신 url로 처리하도록 sell 페이지 수정 예정

    private Long auctionStartPrice;
    private Long auctionMinBidIncrement;
    private String auctionEndAt;

    public ProductType getEffectiveType() {
        if (type != null) return type;
        if (Boolean.TRUE.equals(isAuction)) return ProductType.AUCTION;
        return ProductType.NORMAL;
    }
}
