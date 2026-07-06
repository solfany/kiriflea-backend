package com.nplohs.market.chat.entity;

import com.nplohs.market.auth.entity.User;
import com.nplohs.market.product.entity.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@org.hibernate.annotations.Comment("채팅방")
@Table(name = "chat_rooms",
       uniqueConstraints = @UniqueConstraint(columnNames = {"buyer_id", "seller_id", "product_id"}))
@Getter
@NoArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String lastMessage;

    private LocalDateTime lastMessageAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean buyerLeft = false;

    @Column(nullable = false)
    private boolean sellerLeft = false;

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
    }

    public void setSellerLeft(boolean left) {
        this.sellerLeft = left;
    }
}
