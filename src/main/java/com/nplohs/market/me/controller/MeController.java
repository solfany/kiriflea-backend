package com.nplohs.market.me.controller;

import com.nplohs.market.auth.entity.User;
import com.nplohs.market.auth.repository.UserRepository;
import com.nplohs.market.common.response.ApiResponse;
import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.repository.ProductRepository;
import com.nplohs.market.wishlist.repository.WishlistRepository;
import com.nplohs.market.trade.entity.Review;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UserRepository     userRepository;
    private final ProductRepository  productRepository;
    private final WishlistRepository wishlistRepository;
    private final com.nplohs.market.trade.repository.TradeRepository tradeRepository;
    private final com.nplohs.market.trade.repository.ReviewRepository reviewRepository;
    private final com.nplohs.market.auction.repository.AuctionRepository auctionRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** GET /api/me/profile — 내 프로필 조회 */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = findUser(userDetails);
        Map<String, Object> res = new HashMap<>();
        res.put("id", user.getId());
        res.put("email", user.getEmail());
        res.put("name", user.getName());
        res.put("nickname", user.getNickname());
        res.put("profileImage", user.getProfileImage());
        res.put("mannerScore", user.getMannerScore());
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    /** PATCH /api/me/profile — 내 프로필 수정 */
    @org.springframework.web.bind.annotation.PatchMapping("/profile")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @org.springframework.web.bind.annotation.RequestBody Map<String, String> body) {
        User user = findUser(userDetails);
        
        String name = body.get("name");
        String nickname = body.get("nickname");
        String profileImage = body.get("profileImage");
        
        if (nickname != null && !nickname.equals(user.getNickname()) && userRepository.existsByNickname(nickname)) {
            throw new IllegalArgumentException("이미 사용중인 닉네임입니다.");
        }
        
        user.changeProfile(name, nickname, profileImage);
        
        Map<String, Object> res = new HashMap<>();
        res.put("id", user.getId());
        res.put("email", user.getEmail());
        res.put("name", user.getName());
        res.put("nickname", user.getNickname());
        res.put("profileImage", user.getProfileImage());
        res.put("mannerScore", user.getMannerScore());
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    /** GET /api/me/likes — 내가 찜한 상품 목록 */
    @GetMapping("/likes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myLikes(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String cursor) {

        User user = findUser(userDetails);

        List<Map<String, Object>> items = wishlistRepository
                .findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(w -> productToItem(w.getProduct(), true, user.getId()))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(cursorPage(items)));
    }

    /** GET /api/me/listings — 내 판매 목록 */
    @GetMapping("/listings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myListings(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String cursor) {

        User user = findUser(userDetails);

        List<Map<String, Object>> items = productRepository
                .findBySeller_IdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(p -> !p.isDeleted())
                .filter(p -> {
                    if ("HIDDEN".equalsIgnoreCase(tab)) {
                        return p.isHidden();
                    }
                    if (p.isHidden()) return false;
                    
                    if ("SOLD".equalsIgnoreCase(tab)) {
                        return p.getStatus() == com.nplohs.market.product.entity.ProductStatus.SOLD;
                    }
                    // default: SALE (판매중/예약중/경매중)
                    return p.getStatus() != com.nplohs.market.product.entity.ProductStatus.SOLD;
                })
                .map(p -> productToItem(p, false, user.getId()))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(cursorPage(items)));
    }

    /** GET /api/me/purchases — 내 구매 목록 */
    @GetMapping("/purchases")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myPurchases(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String cursor) {

        User user = findUser(userDetails);

        // 구매 내역은 Trade 엔티티에서 조회
        List<Map<String, Object>> items = tradeRepository
                .findByBuyer_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(t -> productToItem(t.getProduct(), false, user.getId()))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(cursorPage(items)));
    }

    /** GET /api/me/reviews — 받은 거래 후기 목록 */
    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myReviews(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String cursor) {
        User user = findUser(userDetails);

        List<Map<String, Object>> items = reviewRepository
                .findByReviewee_IdAndIsHiddenFalseOrderByCreatedAtDesc(user.getId()).stream()
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

        return ResponseEntity.ok(ApiResponse.ok(cursorPage(items)));
    }

    private Map<String, Object> productToItem(Product p, boolean isLiked, Long currentUserId) {
        List<String> urls = p.getImages().stream()
                .map(img -> img.getImageUrl())
                .toList();
        Map<String, Object> m = new HashMap<>();
        m.put("id",         p.getId());
        m.put("title",      p.getTitle());
        m.put("price",      p.getPrice() != null ? p.getPrice() : 0L);
        m.put("status",     p.getStatus().name());
        m.put("imageUrls",  urls);
        m.put("wishCount",  p.getWishCount());
        m.put("viewCount",  p.getViewCount());
        m.put("isLiked",    isLiked);
        m.put("category",   p.getCategory() != null ? p.getCategory().name() : null);
        m.put("isAuction",  "AUCTION".equals(p.getType().name()));
        m.put("isHidden",   p.isHidden());
        m.put("isDeleted",  p.isDeleted());
        m.put("createdAt",  p.getCreatedAt().format(FMT));
        
        if ("AUCTION".equals(p.getType().name())) {
            auctionRepository.findByProduct_Id(p.getId()).ifPresent(auction -> {
                m.put("currentBid", auction.getCurrentPrice().longValue());
                m.put("bidCount", auction.getBidCount());
            });
        }
        
        if (p.getStatus() == com.nplohs.market.product.entity.ProductStatus.SOLD) {
            tradeRepository.findByProduct_Id(p.getId()).ifPresent(trade -> {
                m.put("tradeId", trade.getId());
                boolean isSeller = trade.getSeller().getId().equals(currentUserId);
                m.put("partnerNickname", isSeller ? trade.getBuyer().getNickname() : trade.getSeller().getNickname());
                boolean isReviewed = reviewRepository.findByTrade_IdAndReviewer_Id(trade.getId(), currentUserId).isPresent();
                m.put("isReviewed", isReviewed);
            });
        }
        
        System.out.println("Mapped product to item: " + m);
        return m;
    }

    private Map<String, Object> cursorPage(List<Map<String, Object>> items) {
        System.out.println("Returning cursorPage with items size: " + items.size());
        Map<String, Object> page = new HashMap<>();
        page.put("items",      items);
        page.put("nextCursor", null);
        page.put("hasMore",    false);
        return page;
    }

    private User findUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
