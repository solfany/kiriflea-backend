package com.nplohs.market.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ProductListResponse {
    private List<ProductResponse> items;
    private String nextCursor;   // base64-encoded "createdAt|id"
    private boolean hasMore;
}
