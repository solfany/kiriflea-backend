package com.nplohs.market.product.entity;

import org.hibernate.annotations.Comment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Comment("상품 이미지")
@Table(name = "product_images")
@Getter
@NoArgsConstructor
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("고유 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @Comment("상품")
    private Product product;

    @Column(nullable = false)
    @Comment("이미지 URL")
    private String imageUrl;

    @Column(nullable = false)
    @Comment("정렬 순서")
    private int orderIndex;

    public ProductImage(Product product, String imageUrl, int orderIndex) {
        this.product    = product;
        this.imageUrl   = imageUrl;
        this.orderIndex = orderIndex;
    }
}
