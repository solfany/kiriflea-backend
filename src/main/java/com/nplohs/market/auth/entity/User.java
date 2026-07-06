package com.nplohs.market.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@org.hibernate.annotations.Comment("사용자 정보")
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false, unique = true, length = 15)
    private String nickname;

    @Column(length = 255)
    private String profileImage;

    @Column(length = 13)
    private String phoneNumber;

    @Column(nullable = false)
    private boolean phoneVisible = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.ROLE_USER;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private double mannerScore = 36.5;

    @Column(nullable = false)
    private int loginFailCount = 0;

    private LocalDateTime lockedUntil;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public User(String email, String password, String name, String nickname) {
        this.email    = email;
        this.password = password;
        this.name     = name;
        this.nickname = nickname;
    }

    public void verifyEmail()                    { this.emailVerified = true; }
    public void changePassword(String encoded)   { this.password = encoded; }
    public void changeNickname(String nickname)  { this.nickname = nickname; }
    public void changeProfile(String name, String nickname, String profileImage) {
        if (name != null) this.name = name;
        if (nickname != null) this.nickname = nickname;
        if (profileImage != null) this.profileImage = profileImage;
    }
    public void changePhone(String phone, boolean visible) {
        this.phoneNumber = phone;
        this.phoneVisible = visible;
    }

    public void recordLoginFail(int lockMinutes) {
        this.loginFailCount++;
        if (this.loginFailCount >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(lockMinutes);
        }
    }

    public void resetLoginFail() {
        this.loginFailCount = 0;
        this.lockedUntil    = null;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public void addMannerScore(double diff) {
        this.mannerScore = Math.min(100.0, Math.max(0.0, this.mannerScore + diff));
    }
}
