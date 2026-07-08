package com.nplohs.market.auction.controller;

import com.nplohs.market.auction.dto.AuctionDto;
import com.nplohs.market.auction.service.AuctionService;
import com.nplohs.market.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

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
        java.util.List<AuctionDto.MyBidResponse> items =
            auctionService.myBids(userDetails.getUsername(), 0, size);
        java.util.Map<String, Object> page = new java.util.HashMap<>();
        page.put("items",      items);
        page.put("nextCursor", null);
        page.put("hasMore",    false);
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

    // 최고 입찰 취소 (판매자)
    @DeleteMapping("/api/products/{productId}/auctions/top-bid")
    public ResponseEntity<ApiResponse<Void>> cancelTopBid(
        @PathVariable Long productId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        auctionService.cancelTopBid(productId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
