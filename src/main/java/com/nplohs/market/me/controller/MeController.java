package com.nplohs.market.me.controller;

import com.nplohs.market.auction.entity.Auction;
import com.nplohs.market.auth.entity.User;
import com.nplohs.market.auth.repository.UserRepository;
import com.nplohs.market.common.response.ApiResponse;
import com.nplohs.market.me.dto.ProfileUpdateRequest;
import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.repository.ProductRepository;
import com.nplohs.market.trade.entity.Trade;
import com.nplohs.market.wishlist.entity.Wishlist;
import com.nplohs.market.wishlist.repository.WishlistRepository;
import com.nplohs.market.trade.entity.Review;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            @org.springframework.web.bind.annotation.RequestBody @Valid ProfileUpdateRequest body) {
        User user = findUser(userDetails);

        String name = body.name();
        String nickname = body.nickname();
        String profileImage = body.profileImage();

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

    private static final int PAGE_SIZE = 20;

    /** GET /api/me/likes — 내가 찜한 상품 목록 */
    @GetMapping("/likes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myLikes(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String cursor) {

        User user = findUser(userDetails);

        List<Wishlist> all = wishlistRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
        Slice<Wishlist> sliced = paginate(all, cursor, Wishlist::getId);
        List<Product> products = sliced.page().stream().map(Wishlist::getProduct).toList();
        Set<Long> likedIds = products.stream().map(Product::getId).collect(Collectors.toSet());

        List<Map<String, Object>> items = toItems(products, user.getId(), likedIds);
        return ResponseEntity.ok(ApiResponse.ok(cursorPage(items, sliced.nextCursor(), sliced.hasMore())));
    }

    /** GET /api/me/listings — 내 판매 목록 */
    @GetMapping("/listings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myListings(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String cursor) {

        User user = findUser(userDetails);

        List<Product> all = productRepository.findBySeller_IdOrderByCreatedAtDesc(user.getId()).stream()
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
                .toList();

        Slice<Product> sliced = paginate(all, cursor, Product::getId);
        List<Map<String, Object>> items = toItems(sliced.page(), user.getId(), Set.of());
        return ResponseEntity.ok(ApiResponse.ok(cursorPage(items, sliced.nextCursor(), sliced.hasMore())));
    }

    /** GET /api/me/purchases — 내 구매 목록 */
    @GetMapping("/purchases")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myPurchases(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String cursor) {

        User user = findUser(userDetails);

        // 구매 내역은 Trade 엔티티에서 조회
        List<Trade> all = tradeRepository.findByBuyer_IdOrderByCreatedAtDesc(user.getId());
        Slice<Trade> sliced = paginate(all, cursor, Trade::getId);
        List<Product> products = sliced.page().stream().map(Trade::getProduct).toList();

        List<Map<String, Object>> items = toItems(products, user.getId(), Set.of());
        return ResponseEntity.ok(ApiResponse.ok(cursorPage(items, sliced.nextCursor(), sliced.hasMore())));
    }

    /** GET /api/me/reviews — 받은 거래 후기 목록 */
    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myReviews(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String cursor) {
        User user = findUser(userDetails);

        List<Review> all = reviewRepository.findByReviewee_IdAndIsHiddenFalseOrderByCreatedAtDesc(user.getId());
        Slice<Review> sliced = paginate(all, cursor, Review::getId);

        List<Map<String, Object>> items = sliced.page().stream()
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

        return ResponseEntity.ok(ApiResponse.ok(cursorPage(items, sliced.nextCursor(), sliced.hasMore())));
    }

    // 상품 목록(찜/판매/구매)마다 경매/거래/후기 여부를 개별 조회하던 N+1을 없애기 위해,
    // 페이지 안의 상품들에 대해서만 한 번씩 배치 조회한다.
    private List<Map<String, Object>> toItems(List<Product> products, Long currentUserId, Set<Long> likedProductIds) {
        if (products.isEmpty()) return List.of();

        Map<Long, Auction> auctionByProduct = auctionRepository.findByProductIn(products).stream()
                .collect(Collectors.toMap(a -> a.getProduct().getId(), a -> a));

        List<Long> soldProductIds = products.stream()
                .filter(p -> p.getStatus() == com.nplohs.market.product.entity.ProductStatus.SOLD)
                .map(Product::getId)
                .toList();
        List<Trade> trades = soldProductIds.isEmpty() ? List.of() : tradeRepository.findByProduct_IdIn(soldProductIds);
        Map<Long, Trade> tradeByProduct = trades.stream()
                .collect(Collectors.toMap(t -> t.getProduct().getId(), t -> t));

        List<Long> tradeIds = trades.stream().map(Trade::getId).toList();
        Set<Long> reviewedTradeIds = tradeIds.isEmpty()
                ? Set.of()
                : reviewRepository.findByTrade_IdInAndReviewer_Id(tradeIds, currentUserId).stream()
                    .map(r -> r.getTrade().getId())
                    .collect(Collectors.toSet());

        return products.stream()
                .map(p -> productToItem(
                        p,
                        likedProductIds.contains(p.getId()),
                        currentUserId,
                        auctionByProduct.get(p.getId()),
                        tradeByProduct.get(p.getId()),
                        reviewedTradeIds))
                .toList();
    }

    private Map<String, Object> productToItem(Product p, boolean isLiked, Long currentUserId,
            Auction auction, Trade trade, Set<Long> reviewedTradeIds) {
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

        if (auction != null) {
            m.put("currentBid", auction.getCurrentPrice().longValue());
            m.put("bidCount", auction.getBidCount());
        }

        if (trade != null) {
            m.put("tradeId", trade.getId());
            boolean isSeller = trade.getSeller().getId().equals(currentUserId);
            m.put("partnerNickname", isSeller ? trade.getBuyer().getNickname() : trade.getSeller().getNickname());
            m.put("isReviewed", reviewedTradeIds.contains(trade.getId()));
        }

        return m;
    }

    private Map<String, Object> cursorPage(List<Map<String, Object>> items, String nextCursor, boolean hasMore) {
        Map<String, Object> page = new HashMap<>();
        page.put("items",      items);
        page.put("nextCursor", nextCursor);
        page.put("hasMore",    hasMore);
        return page;
    }

    // 이미 createdAt DESC로 정렬된 리스트를, 마지막으로 본 항목의 id(cursor) 다음부터 PAGE_SIZE개 잘라낸다.
    private <T> Slice<T> paginate(List<T> all, String cursor, Function<T, Long> idFn) {
        int startIndex = 0;
        if (cursor != null) {
            try {
                long cursorId = Long.parseLong(cursor);
                for (int i = 0; i < all.size(); i++) {
                    if (idFn.apply(all.get(i)).equals(cursorId)) {
                        startIndex = i + 1;
                        break;
                    }
                }
            } catch (NumberFormatException ignored) {
                // 잘못된 cursor면 처음부터
            }
        }
        int endIndex = Math.min(startIndex + PAGE_SIZE, all.size());
        List<T> page = startIndex >= all.size() ? List.of() : all.subList(startIndex, endIndex);
        boolean hasMore = endIndex < all.size();
        String nextCursor = (hasMore && !page.isEmpty()) ? idFn.apply(page.get(page.size() - 1)).toString() : null;
        return new Slice<>(page, nextCursor, hasMore);
    }

    private record Slice<T>(List<T> page, String nextCursor, boolean hasMore) {}

    private User findUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
