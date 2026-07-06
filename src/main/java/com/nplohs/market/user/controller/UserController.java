package com.nplohs.market.user.controller;

import com.nplohs.market.auth.entity.User;
import com.nplohs.market.auth.repository.UserRepository;
import com.nplohs.market.common.response.ApiResponse;
import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.repository.ProductRepository;
import com.nplohs.market.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

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
        
        DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> response = products.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("price", p.getPrice() != null ? p.getPrice() : 0L);
            m.put("status", p.getStatus().name());
            
            List<String> urls = p.getImages().stream()
                    .map(img -> img.getImageUrl())
                    .collect(Collectors.toList());
            m.put("imageUrls", urls);
            
            m.put("wishCount", p.getWishCount());
            m.put("viewCount", p.getViewCount());
            m.put("category", p.getCategory() != null ? p.getCategory().name() : null);
            m.put("isAuction", "AUCTION".equals(p.getType().name()));
            m.put("createdAt", p.getCreatedAt().format(FMT));
            return m;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
