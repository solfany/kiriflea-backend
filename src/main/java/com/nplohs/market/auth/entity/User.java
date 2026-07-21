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
    @org.hibernate.annotations.Comment("사용자 고유 ID")
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    @org.hibernate.annotations.Comment("이메일 (로그인 ID)")
    private String email;

    @Column(nullable = false)
    @org.hibernate.annotations.Comment("비밀번호 (암호화)")
    private String password;

    @Column(nullable = false, length = 20)
    @org.hibernate.annotations.Comment("실명")
    private String name;

    @Column(nullable = false, unique = true, length = 15)
    @org.hibernate.annotations.Comment("닉네임 (상점명)")
    private String nickname;

    @Column(length = 255)
    @org.hibernate.annotations.Comment("프로필 이미지 URL")
    private String profileImage;

    @Column(length = 13)
    @org.hibernate.annotations.Comment("휴대폰 번호")
    private String phoneNumber;

    @Column(nullable = false)
    @org.hibernate.annotations.Comment("휴대폰 번호 공개 여부")
    private boolean phoneVisible = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @org.hibernate.annotations.Comment("권한 (USER/ADMIN)")
    private Role role = Role.ROLE_USER;

    @Column(nullable = false)
    @org.hibernate.annotations.Comment("이메일 인증 여부")
    private boolean emailVerified = false;

    @Column(nullable = false)
    @org.hibernate.annotations.Comment("계정 활성화 상태")
    private boolean active = true;

    @Column
    @org.hibernate.annotations.Comment("탈퇴 일시 (24시간 유예)")
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    @org.hibernate.annotations.Comment("매너 온도 (점수)")
    private double mannerScore = 0.0;

    @Column(nullable = false)
    @org.hibernate.annotations.Comment("연속 로그인 실패 횟수")
    private int loginFailCount = 0;

    @org.hibernate.annotations.Comment("계정 잠금 해제 일시")
    private LocalDateTime lockedUntil;

    @Column(nullable = false, updatable = false)
    @org.hibernate.annotations.Comment("가입 일시")
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

    public void recordLoginFail(int maxFailCount, int lockMinutes) {
        this.loginFailCount++;
        if (this.loginFailCount >= maxFailCount) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(lockMinutes);
        }
    }

    public void withdraw() {
        this.active = false;
        this.deletedAt = LocalDateTime.now();
    }

    public void clearDeletedAt() {
        this.deletedAt = null;
    }

    public void scramblePersonalInfo(String uuid) {
        // email, nickname은 unique 제약조건이 있으므로 난수 조합
        this.email = "deleted_" + uuid + "@kiriflea.local";
        this.nickname = "탈퇴_" + uuid.substring(0, 8);
        this.password = uuid; // 랜덤 패스워드로 덮어쓰기 (로그인 불가)
        this.name = "알 수 없음";
        this.phoneNumber = null;
        this.profileImage = null;
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
