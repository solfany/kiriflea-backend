package com.nplohs.market.wishlist.entity;

import com.nplohs.market.auth.entity.User;
import com.nplohs.market.product.entity.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@org.hibernate.annotations.Comment("사용자 관심 상품(찜)")
@Table(name = "wishlists",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
@Getter
@NoArgsConstructor
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @org.hibernate.annotations.Comment("고유 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @org.hibernate.annotations.Comment("사용자")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @org.hibernate.annotations.Comment("상품")
    private Product product;

    @Column(nullable = false, updatable = false)
    @org.hibernate.annotations.Comment("생성 일시")

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public Wishlist(User user, Product product) {
        this.user    = user;
        this.product = product;
    }
}
