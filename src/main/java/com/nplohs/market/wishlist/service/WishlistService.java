package com.nplohs.market.wishlist.service;

import com.nplohs.market.auth.entity.User;
import com.nplohs.market.auth.repository.UserRepository;
import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.repository.ProductRepository;
import com.nplohs.market.wishlist.entity.Wishlist;
import com.nplohs.market.wishlist.repository.WishlistRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository     userRepository;
    private final ProductRepository  productRepository;
    private final com.nplohs.market.notification.service.NotificationService notificationService;

    /** 찜 등록/취소 토글 — 결과: {wished: true/false} */
    @Transactional
    public Map<String, Object> toggle(String userEmail, Long productId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

        boolean exists = wishlistRepository.existsByUser_IdAndProduct_Id(user.getId(), productId);
        if (exists) {
            wishlistRepository.deleteByUser_IdAndProduct_Id(user.getId(), productId);
            product.decrementWishCount();
            productRepository.save(product);

            // 찜 취소 시 기존 알림 제거 (자신이 자신의 상품을 찜한 경우는 알림이 없으므로 제외)
            if (!product.getSeller().getId().equals(user.getId())) {
                notificationService.deleteLikeNotification(
                    product.getSeller().getId(),
                    "/products/" + product.getId(),
                    user.getNickname()
                );
            }

            return Map.of("wished", false, "count", product.getWishCount());
        } else {
            wishlistRepository.save(new Wishlist(user, product));
            product.incrementWishCount();
            productRepository.save(product);

            // 알림 발송 (자신이 자신의 상품을 찜한 경우는 제외, 중복 알림 방지)
            if (!product.getSeller().getId().equals(user.getId())) {
                boolean alreadyNotified = notificationService.existsLikeNotification(
                    product.getSeller().getId(),
                    "/products/" + product.getId(),
                    user.getNickname()
                );
                if (!alreadyNotified) {
                    notificationService.createNotification(
                        product.getSeller(),
                        com.nplohs.market.notification.entity.NotificationType.LIKE,
                        user.getNickname() + "님이 '" + product.getTitle() + "' 상품을 찜했습니다.",
                        "/products/" + product.getId()
                    );
                }
            }

            return Map.of("wished", true, "count", product.getWishCount());
        }
    }

    /** 찜 목록 조회 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getList(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        return wishlistRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(w -> Map.of(
                        "productId",  (Object) w.getProduct().getId(),
                        "title",      w.getProduct().getTitle(),
                        "price",      w.getProduct().getPrice() != null ? w.getProduct().getPrice() : 0L,
                        "status",     w.getProduct().getStatus().name(),
                        "imageUrl",   w.getProduct().getImages().isEmpty() ? "" : w.getProduct().getImages().get(0).getImageUrl(),
                        "isDeleted",  w.getProduct().isDeleted(),
                        "wishedAt",   w.getCreatedAt().toString()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isWished(String userEmail, Long productId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return wishlistRepository.existsByUser_IdAndProduct_Id(user.getId(), productId);
    }
}
