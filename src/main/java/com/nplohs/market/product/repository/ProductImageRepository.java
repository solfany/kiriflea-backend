package com.nplohs.market.product.repository;

import com.nplohs.market.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Query("SELECT pi.product.seller.id FROM ProductImage pi WHERE pi.imageUrl LIKE CONCAT('%', :key)")
    Optional<Long> findOwnerIdByKey(@Param("key") String key);
}
