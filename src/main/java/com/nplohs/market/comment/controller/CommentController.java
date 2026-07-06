package com.nplohs.market.comment.controller;

import com.nplohs.market.comment.service.CommentService;
import com.nplohs.market.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** GET /api/products/{productId}/comments */
    @GetMapping("/api/products/{productId}/comments")
    public ResponseEntity<ApiResponse<List<CommentService.CommentResponse>>> list(
            @PathVariable Long productId,
            @AuthenticationPrincipal UserDetails user) {
        String email = user != null ? user.getUsername() : null;
        return ResponseEntity.ok(ApiResponse.ok(commentService.list(productId, email)));
    }

    /** POST /api/products/{productId}/comments */
    @PostMapping("/api/products/{productId}/comments")
    public ResponseEntity<ApiResponse<CommentService.CommentResponse>> create(
            @PathVariable Long productId,
            @AuthenticationPrincipal UserDetails user,
            @RequestBody CommentService.CreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.create(productId, user.getUsername(), req)));
    }

    /** DELETE /api/products/{productId}/comments/{commentId} */
    @DeleteMapping("/api/products/{productId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long productId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetails user) {
        commentService.delete(commentId, user.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
