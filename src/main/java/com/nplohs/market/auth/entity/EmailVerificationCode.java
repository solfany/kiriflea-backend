package com.nplohs.market.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Comment("이메일 인증 코드")
@Table(name = "email_verification_codes")
@Getter
@NoArgsConstructor
public class EmailVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("고유 ID")
    private Long id;

    @Column(nullable = false, length = 100)
    @Comment("이메일")
    private String email;

    @Column(nullable = false, length = 6)
    @Comment("코드")
    private String code;

    @Column(nullable = false)
    @Comment("만료 일시")
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Comment("used")
    private boolean used = false;

    public EmailVerificationCode(String email, String code, int expiryMinutes) {
        this.email     = email;
        this.code      = code;
        this.expiresAt = LocalDateTime.now().plusMinutes(expiryMinutes);
    }

    public boolean isExpired() { return LocalDateTime.now().isAfter(expiresAt); }

    public void markUsed() { this.used = true; }
}
