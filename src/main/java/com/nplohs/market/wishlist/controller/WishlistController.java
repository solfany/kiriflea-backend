package com.nplohs.market.wishlist.controller;

import com.nplohs.market.common.response.ApiResponse;
import com.nplohs.market.wishlist.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    /** POST /api/wishlist/{productId} — 찜 토글 */
    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggle(
            @PathVariable Long productId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(wishlistService.toggle(user.getUsername(), productId)));
    }

    /** GET /api/wishlist — 찜 목록 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getList(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(wishlistService.getList(user.getUsername())));
    }

    /** GET /api/wishlist/{productId}/status — 찜 여부 확인 */
    @GetMapping("/{productId}/status")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> status(
            @PathVariable Long productId,
            @AuthenticationPrincipal UserDetails user) {
        boolean wished = wishlistService.isWished(user.getUsername(), productId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("wished", wished)));
    }
}
