package com.nplohs.market.auction.controller;

import com.nplohs.market.auction.dto.AuctionDto;
import com.nplohs.market.auction.service.AuctionService;
import com.nplohs.market.common.ratelimit.RateLimiter;
import com.nplohs.market.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;
    private final RateLimiter    rateLimiter;

    private static final int    BID_MAX_PER_WINDOW = 5;
    private static final Duration BID_WINDOW = Duration.ofSeconds(10);

    // 상품별 입찰 목록 조회
    @GetMapping("/api/products/{productId}/bids")
    public ResponseEntity<ApiResponse<List<AuctionDto.BidResponse>>> getBids(
        @PathVariable Long productId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(auctionService.getBids(productId)));
    }

    // 입찰하기
    @PostMapping("/api/products/{productId}/bids")
    public ResponseEntity<ApiResponse<AuctionDto.BidResponse>> placeBid(
        @PathVariable Long productId,
        @RequestBody Map<String, Long> body,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long amount = body.get("amount");
        if (amount == null || amount <= 0) {
            @SuppressWarnings("unchecked")
            ResponseEntity<ApiResponse<AuctionDto.BidResponse>> err =
                (ResponseEntity<ApiResponse<AuctionDto.BidResponse>>)(ResponseEntity<?>)
                ResponseEntity.badRequest().body(ApiResponse.error("amount는 0보다 커야 합니다."));
            return err;
        }
        if (!rateLimiter.tryAcquire("bid:" + userDetails.getUsername() + ":" + productId, BID_MAX_PER_WINDOW, BID_WINDOW)) {
            @SuppressWarnings("unchecked")
            ResponseEntity<ApiResponse<AuctionDto.BidResponse>> err =
                (ResponseEntity<ApiResponse<AuctionDto.BidResponse>>)(ResponseEntity<?>)
                ResponseEntity.status(429).body(ApiResponse.error("입찰이 너무 빠릅니다. 잠시 후 다시 시도해주세요."));
            return err;
        }
        AuctionDto.BidResponse bid = auctionService.placeBid(
            productId, userDetails.getUsername(), amount);
        return ResponseEntity.ok(ApiResponse.ok(bid));
    }

    // 내 입찰 내역
    @GetMapping("/api/me/bids")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> myBids(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        int pageNumber = 0;
        if (cursor != null) {
            try {
                pageNumber = Integer.parseInt(cursor);
            } catch (NumberFormatException ignored) {
                // 잘못된 cursor면 첫 페이지부터
            }
        }

        org.springframework.data.domain.Page<AuctionDto.MyBidResponse> result =
            auctionService.myBids(userDetails.getUsername(), pageNumber, size);

        java.util.Map<String, Object> page = new java.util.HashMap<>();
        page.put("items",      result.getContent());
        page.put("nextCursor", result.hasNext() ? String.valueOf(pageNumber + 1) : null);
        page.put("hasMore",    result.hasNext());
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    // 거래 완료 (낙찰자)
    @PostMapping("/api/products/{productId}/auction/trade")
    public ResponseEntity<ApiResponse<Void>> completeTrade(
        @PathVariable Long productId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        auctionService.completeTrade(productId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // 조기 낙찰 (판매자)
    @PostMapping("/api/products/{productId}/auctions/close-early")
    public ResponseEntity<ApiResponse<Void>> closeEarly(
        @PathVariable Long productId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        auctionService.closeEarly(productId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // 조기 낙찰 취소 (판매자)
    @PostMapping("/api/products/{productId}/auctions/cancel-early-close")
    public ResponseEntity<ApiResponse<Void>> cancelEarlyClose(
        @PathVariable Long productId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        auctionService.cancelEarlyClose(productId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // 경매 재오픈 (판매자)
    @PostMapping("/api/products/{productId}/auctions/reopen")
    public ResponseEntity<ApiResponse<Void>> reopen(
        @PathVariable Long productId,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        java.time.LocalDateTime newEndAt = java.time.LocalDateTime.parse(body.get("endAt"));
        auctionService.reopen(productId, userDetails.getUsername(), newEndAt);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // 경매 시간 연장 (판매자)
    @PatchMapping("/api/products/{productId}/auctions/extend")
    public ResponseEntity<ApiResponse<Void>> extendTime(
        @PathVariable Long productId,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        java.time.LocalDateTime newEndAt = java.time.LocalDateTime.parse(body.get("endAt"));
        auctionService.extendTime(productId, userDetails.getUsername(), newEndAt);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
    // 내 입찰 철회 (구매자)
    @DeleteMapping("/api/products/{productId}/auctions/my-bid")
    public ResponseEntity<ApiResponse<Void>> withdrawMyBid(
        @PathVariable Long productId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        auctionService.withdrawMyBid(productId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
