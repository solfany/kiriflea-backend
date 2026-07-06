package com.nplohs.market.product.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@org.hibernate.annotations.Comment("상품 이미지")
@Table(name = "product_images")
@Getter
@NoArgsConstructor
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private int orderIndex;

    public ProductImage(Product product, String imageUrl, int orderIndex) {
        this.product    = product;
        this.imageUrl   = imageUrl;
        this.orderIndex = orderIndex;
    }
}
