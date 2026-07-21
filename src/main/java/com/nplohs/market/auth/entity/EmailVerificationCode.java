package com.nplohs.market.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@org.hibernate.annotations.Comment("이메일 인증 코드")
@Table(name = "email_verification_codes")
@Getter
@NoArgsConstructor
public class EmailVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @org.hibernate.annotations.Comment("고유 ID")
    private Long id;

    @Column(nullable = false, length = 100)
    @org.hibernate.annotations.Comment("이메일")

    private String email;

    @Column(nullable = false, length = 6)
    @org.hibernate.annotations.Comment("코드")

    private String code;

    @Column(nullable = false)
    @org.hibernate.annotations.Comment("만료 일시")

    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    public EmailVerificationCode(String email, String code, int expiryMinutes) {
        this.email     = email;
        this.code      = code;
        this.expiresAt = LocalDateTime.now().plusMinutes(expiryMinutes);
    }

    public boolean isExpired() { return LocalDateTime.now().isAfter(expiresAt); }

    public void markUsed() { this.used = true; }
}
