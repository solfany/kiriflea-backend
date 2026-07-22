package com.nplohs.market.product.entity;

import com.nplohs.market.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Comment("판매/경매 상품 정보")
@Table(name = "products")
@Getter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("상품 고유 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    @Comment("판매자")
    private User seller;

    @Column(nullable = false, length = 100)
    @Comment("제목")
    private String title;

    @Column(columnDefinition = "TEXT")
    @Comment("상품 설명")
    private String description;

    @Comment("가격")
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Comment("카테고리")
    private ProductCategory category = ProductCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Comment("상품 판매 상태")
    private ProductStatus status = ProductStatus.SALE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Comment("타입")
    private ProductType type = ProductType.NORMAL;

    @Column(nullable = false)
    @Comment("조회수")
    private int viewCount = 0;

    @Column(nullable = false)
    @Comment("관심수")
    private int wishCount = 0;

    @Column(nullable = false, updatable = false)
    @Comment("등록 일시")
    private LocalDateTime createdAt;

    @Comment("수정 일시")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    @Comment("삭제 여부")
    private boolean isDeleted = false;

    @Column(nullable = false)
    @Comment("숨김 여부")
    private boolean isHidden = false;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Comment("images")
    private List<ProductImage> images = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Product(User seller, String title, String description, Long price,
                   ProductCategory category, ProductType type) {
        this.seller      = seller;
        this.title       = title;
        this.description = description;
        this.price       = price;
        this.category    = category;
        this.type        = type;
        this.status      = type == ProductType.AUCTION ? ProductStatus.AUCTION : ProductStatus.SALE;
    }

    public void update(String title, String description, Long price, ProductCategory category) {
        this.title       = title;
        this.description = description;
        this.price       = price;
        this.category    = category;
    }

    public void changeStatus(ProductStatus status) { this.status = status; }

    public void incrementViewCount()   { this.viewCount++; }
    public void incrementWishCount()   { this.wishCount++; }
    public void decrementWishCount()   { if (this.wishCount > 0) this.wishCount--; }

    public void delete() { this.isDeleted = true; }
    public void hide() { this.isHidden = true; }
    public void unhide() { this.isHidden = false; }
}
