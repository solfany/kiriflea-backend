package com.nplohs.market.auth.service;

import com.nplohs.market.auth.dto.*;
import com.nplohs.market.auth.entity.User;
import com.nplohs.market.auth.repository.UserRepository;
import com.nplohs.market.config.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository  userRepository;
    private final EmailService    emailService;
    private final NicknameService nicknameService;
    private final JwtService      jwtService;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redis;

    @Value("${nplohs.login.lock-minutes:15}")
    private int lockMinutes;

    @Value("${jwt.refresh-expiry-ms}")
    private long refreshExpiryMs;

    private static final String REFRESH_PREFIX = "refresh:";

    // ── 회원가입 ──────────────────────────────────────────────────
    @Transactional
    public TokenResponse signUp(SignUpRequest request) {
        String email = request.getEmail().toLowerCase();

        if (!emailService.isEmailVerified(email))
            throw new IllegalStateException("이메일 인증을 먼저 완료해주세요.");

        if (userRepository.existsByEmail(email))
            throw new IllegalStateException("이미 사용 중인 이메일입니다.");
        if (userRepository.existsByNickname(request.getNickname()))
            throw new IllegalStateException("이미 사용 중인 닉네임입니다.");

        User user = new User(
                email,
                passwordEncoder.encode(request.getPassword()),
                request.getName(),
                request.getNickname()
        );
        user.verifyEmail();

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            user.changePhone(request.getPhoneNumber(), request.isPhoneVisible());
        }

        userRepository.save(user);
        emailService.clearEmailVerified(email);

        return buildTokens(user);
    }

    // ── 비밀번호 재설정 ──────────────────────────────────────────
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        String email = request.getEmail().toLowerCase();

        if (!emailService.isEmailVerified(email)) {
            throw new IllegalStateException("이메일 인증을 먼저 완료해주세요.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        emailService.clearEmailVerified(email);
    }

    // ── 로그인 ────────────────────────────────────────────────────
    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (user.isLocked())
            throw new IllegalStateException("로그인 시도 횟수 초과. 잠시 후 다시 시도하세요.");

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            user.recordLoginFail(lockMinutes);
            userRepository.save(user);
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        user.resetLoginFail();
        userRepository.save(user);

        return buildTokens(user);
    }

    // ── 토큰 갱신 ────────────────────────────────────────────────
    public TokenResponse refresh(String refreshToken) {
        if (!jwtService.isValid(refreshToken))
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.");

        String email  = jwtService.extractEmail(refreshToken);
        Object stored = redis.opsForValue().get(REFRESH_PREFIX + email);
        if (stored == null || !stored.toString().equals(refreshToken))
            throw new IllegalArgumentException("만료된 토큰입니다.");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return buildTokens(user);
    }

    // ── 로그아웃 ─────────────────────────────────────────────────
    public void logout(String email) {
        redis.delete(REFRESH_PREFIX + email);
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────
    private TokenResponse buildTokens(User user) {
        String accessToken  = jwtService.generateAccessToken(user.getEmail(), user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        redis.opsForValue().set(
                REFRESH_PREFIX + user.getEmail(),
                refreshToken,
                refreshExpiryMs,
                TimeUnit.MILLISECONDS
        );

        return new TokenResponse(
                accessToken,
                refreshToken,
                new TokenResponse.UserSummary(user.getId(), user.getEmail(), user.getNickname(), user.getProfileImage(), user.getMannerScore())
        );
    }
}
