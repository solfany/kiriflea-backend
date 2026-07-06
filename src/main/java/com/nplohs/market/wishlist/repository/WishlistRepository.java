package com.nplohs.market.wishlist.repository;

import com.nplohs.market.wishlist.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    Optional<Wishlist> findByUser_IdAndProduct_Id(Long userId, Long productId);
    boolean existsByUser_IdAndProduct_Id(Long userId, Long productId);
    List<Wishlist> findByUser_IdOrderByCreatedAtDesc(Long userId);
    void deleteByUser_IdAndProduct_Id(Long userId, Long productId);
}
