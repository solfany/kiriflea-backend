package com.nplohs.market.chat.entity;

import org.hibernate.annotations.Comment;
import com.nplohs.market.user.entity.User;
import com.nplohs.market.product.entity.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Comment("채팅방")
@Table(name = "chat_rooms",
       uniqueConstraints = @UniqueConstraint(columnNames = {"buyer_id", "seller_id", "product_id"}))
@Getter
@NoArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("고유 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    @Comment("구매자")
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    @Comment("판매자")
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @Comment("상품")
    private Product product;

    @Comment("lastMessage")
    private String lastMessage;

    @Comment("lastMessageAt")
    private LocalDateTime lastMessageAt;

    @Column(nullable = false, updatable = false)
    @Comment("생성 일시")
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @Comment("buyerLeft")
    private boolean buyerLeft = false;

    @Column(nullable = false)
    @Comment("sellerLeft")
    private boolean sellerLeft = false;

    @Comment("buyerLeftAt")
    private LocalDateTime buyerLeftAt;

    @Comment("sellerLeftAt")
    private LocalDateTime sellerLeftAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public ChatRoom(User buyer, User seller, Product product) {
        this.buyer   = buyer;
        this.seller  = seller;
        this.product = product;
    }

    public void updateLastMessage(String message) {
        this.lastMessage   = message;
        this.lastMessageAt = LocalDateTime.now();
    }

    public void setBuyerLeft(boolean left) {
        this.buyerLeft = left;
        if (left) {
            this.buyerLeftAt = LocalDateTime.now();
        }
    }

    public void setSellerLeft(boolean left) {
        this.sellerLeft = left;
        if (left) {
            this.sellerLeftAt = LocalDateTime.now();
        }
    }
}
