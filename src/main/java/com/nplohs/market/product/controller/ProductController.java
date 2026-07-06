package com.nplohs.market.product.controller;

import com.nplohs.market.common.response.ApiResponse;
import com.nplohs.market.product.dto.*;
import com.nplohs.market.product.service.ProductService;
import com.nplohs.market.wishlist.service.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService  productService;
    private final WishlistService wishlistService;

    /** GET /api/products?category=&status=&cursor=&keyword=&sort=LATEST|POPULAR&minPrice=&maxPrice= */
    @GetMapping
    public ResponseEntity<ApiResponse<ProductListResponse>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long   minPrice,
            @RequestParam(required = false) Long   maxPrice,
            @RequestParam(required = false, defaultValue = "LATEST") String sort) {
        String cursorCreatedAt = null;
        Long   cursorId        = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String decoded = new String(java.util.Base64.getDecoder().decode(cursor));
                String[] parts = decoded.split("\\|");
                if (parts.length == 2) {
                    cursorCreatedAt = parts[0];
                    cursorId        = Long.parseLong(parts[1]);
                }
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(ApiResponse.ok(
                productService.list(category, status, cursorCreatedAt, cursorId,
                        keyword, minPrice, maxPrice, sort)));
    }

    /** GET /api/products/trending */
    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> trending() {
        return ResponseEntity.ok(ApiResponse.ok(productService.trending()));
    }

    /** GET /api/products/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getById(id)));
    }

    /** POST /api/products */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody @Valid ProductCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.create(user.getUsername(), request)));
    }

    /** PATCH /api/products/{id} */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user,
            @RequestBody @Valid ProductCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.update(id, user.getUsername(), request)));
    }

    /** PATCH /api/products/{id}/status */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ProductResponse>> changeStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user,
            @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(productService.changeStatus(id, user.getUsername(), body.get("status"))));
    }

    /** DELETE /api/products/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        productService.delete(id, user.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** POST /api/products/{id}/like — 찜 토글, 프론트 기대값: {liked, count} */
    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> like(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        java.util.Map<String, Object> result = wishlistService.toggle(user.getUsername(), id);
        boolean wished = Boolean.TRUE.equals(result.get("wished"));
        Object  count  = result.getOrDefault("count", 0);
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("liked", wished, "count", count)));
    }

    /** POST /api/products/{id}/hide — 상품 숨기기 토글 */
    @PostMapping("/{id}/hide")
    public ResponseEntity<ApiResponse<Void>> toggleHide(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails user) {
        productService.toggleHide(id, user.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
