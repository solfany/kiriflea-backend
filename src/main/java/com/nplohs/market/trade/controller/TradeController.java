package com.nplohs.market.trade.controller;

import com.nplohs.market.trade.dto.TradeDto;
import com.nplohs.market.trade.service.TradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    @PostMapping
    public ResponseEntity<?> completeTrade(@RequestBody TradeDto.CreateTradeRequest request, Authentication authentication) {
        try {
            TradeDto.TradeResponse response = tradeService.completeTrade(authentication.getName(), request);
            return ResponseEntity.ok(Map.of("success", true, "data", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{tradeId}/reviews")
    public ResponseEntity<?> leaveReview(@PathVariable Long tradeId, @RequestBody @Valid TradeDto.CreateReviewRequest request, Authentication authentication) {
        try {
            TradeDto.ReviewResponse response = tradeService.leaveReview(authentication.getName(), tradeId, request);
            return ResponseEntity.ok(Map.of("success", true, "data", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<?> getTradeByProductId(@PathVariable Long productId) {
        TradeDto.TradeResponse response = tradeService.getTradeByProductId(productId);
        return ResponseEntity.ok(Map.of("success", true, "data", response));
    }
}
