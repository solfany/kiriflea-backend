package com.nplohs.market.user.controller;

import com.nplohs.market.user.entity.User;
import com.nplohs.market.user.repository.UserRepository;
import com.nplohs.market.auction.entity.Auction;
import com.nplohs.market.auction.repository.AuctionRepository;
import com.nplohs.market.common.response.ApiResponse;
import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.repository.ProductRepository;
import com.nplohs.market.user.dto.UserResponse;
import com.nplohs.market.trade.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final AuctionRepository auctionRepository;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserInfo(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        int listingCount = productRepository.countBySeller_Id(id);
        return ResponseEntity.ok(ApiResponse.ok(new UserResponse(user, listingCount)));
    }

    @GetMapping("/{id}/listings")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUserListings(@PathVariable Long id) {
        List<Product> products = productRepository.findBySeller_IdOrderByCreatedAtDesc(id);

        // 경매 상품의 auction 정보를 한번에 로드
        List<Auction> auctions = auctionRepository.findByProductIn(products);
        Map<Long, Auction> auctionByProductId = auctions.stream()
                .collect(Collectors.toMap(a -> a.getProduct().getId(), a -> a));

        DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> response = products.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("status", p.getStatus().name());

            List<String> urls = p.getImages().stream()
                    .map(img -> img.getImageUrl())
                    .collect(Collectors.toList());
            m.put("imageUrls", urls);

            m.put("wishCount", p.getWishCount());
            m.put("viewCount", p.getViewCount());
            m.put("category", p.getCategory() != null ? p.getCategory().name() : null);
            m.put("createdAt", p.getCreatedAt().format(FMT));

            boolean isAuction = "AUCTION".equals(p.getType().name());
            m.put("isAuction", isAuction);

            if (isAuction) {
                Auction auction = auctionByProductId.get(p.getId());
                if (auction != null) {
                    m.put("price", auction.getCurrentPrice().longValue());
                    m.put("currentBid", auction.getCurrentPrice().longValue());
                    m.put("bidCount", auction.getBidCount());
                } else {
                    m.put("price", p.getPrice() != null ? p.getPrice() : 0L);
                    m.put("currentBid", 0L);
                    m.put("bidCount", 0);
                }
            } else {
                m.put("price", p.getPrice() != null ? p.getPrice() : 0L);
                m.put("currentBid", null);
                m.put("bidCount", null);
            }

            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        
        user.withdraw();
        userRepository.save(user);
        
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUserReviews(@PathVariable Long id) {
        DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> items = reviewRepository
                .findByReviewee_IdAndIsHiddenFalseOrderByCreatedAtDesc(id).stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId());
                    m.put("score", r.getScore());
                    m.put("comment", r.getComment());
                    m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().format(FMT) : null);
                    
                    Map<String, Object> reviewer = new HashMap<>();
                    reviewer.put("id", r.getReviewer().getId());
                    reviewer.put("nickname", r.getReviewer().getNickname());
                    reviewer.put("profileImage", r.getReviewer().getProfileImage());
                    
                    boolean isBuyer = r.getReviewer().getId().equals(r.getTrade().getBuyer().getId());
                    reviewer.put("role", isBuyer ? "구매자" : "판매자");
                    m.put("reviewer", reviewer);
                    
                    Map<String, Object> product = new HashMap<>();
                    product.put("id", r.getTrade().getProduct().getId());
                    product.put("title", r.getTrade().getProduct().getTitle());
                    m.put("product", product);
                    
                    return m;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(items));
    }
}
