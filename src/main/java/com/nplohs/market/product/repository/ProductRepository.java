package com.nplohs.market.product.repository;

import com.nplohs.market.product.entity.Product;
import com.nplohs.market.product.entity.ProductCategory;
import com.nplohs.market.product.entity.ProductStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Cursor-based pagination with keyword/price/sort support
    @Query("""
        SELECT p FROM Product p
        WHERE p.isDeleted = false AND p.isHidden = false
          AND (:category IS NULL OR p.category = :category)
          AND (:status IS NULL AND p.status != com.nplohs.market.product.entity.ProductStatus.SOLD OR p.status = :status)
          AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (
            :cursorCreatedAt IS NULL OR
            p.createdAt < :cursorCreatedAt OR
            (p.createdAt = :cursorCreatedAt AND p.id < :cursorId)
          )
        ORDER BY p.createdAt DESC, p.id DESC
        """)
    List<Product> findWithCursorLatest(
            @Param("category")        ProductCategory category,
            @Param("status")          ProductStatus status,
            @Param("keyword")         String keyword,
            @Param("minPrice")        Long minPrice,
            @Param("maxPrice")        Long maxPrice,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId")        Long cursorId,
            Pageable pageable
    );

    @Query("""
        SELECT p FROM Product p
        WHERE p.isDeleted = false AND p.isHidden = false
          AND (:category IS NULL OR p.category = :category)
          AND (:status IS NULL AND p.status != com.nplohs.market.product.entity.ProductStatus.SOLD OR p.status = :status)
          AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
        ORDER BY (p.viewCount + p.wishCount * 2) DESC, p.id DESC
        """)
    List<Product> findWithCursorPopular(
            @Param("category")  ProductCategory category,
            @Param("status")    ProductStatus status,
            @Param("keyword")   String keyword,
            @Param("minPrice")  Long minPrice,
            @Param("maxPrice")  Long maxPrice,
            Pageable pageable
    );

    // 급상승 Top 10 (24시간 내, viewCount + wishCount*2 가중 합산)
    @Query("""
        SELECT p FROM Product p
        WHERE p.isDeleted = false AND p.isHidden = false AND p.status != com.nplohs.market.product.entity.ProductStatus.SOLD
          AND (p.createdAt >= :since OR p.updatedAt >= :since)
        ORDER BY (p.viewCount + p.wishCount * 2) DESC
        """)
    List<Product> findTrending(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.seller.id = :sellerId AND p.isDeleted = false ORDER BY p.createdAt DESC")
    List<Product> findBySeller_IdOrderByCreatedAtDesc(@Param("sellerId") Long sellerId);

    int countBySeller_Id(Long sellerId);
}
