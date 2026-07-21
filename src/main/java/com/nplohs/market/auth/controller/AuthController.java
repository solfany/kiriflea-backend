package com.nplohs.market.auth.controller;

import com.nplohs.market.auth.dto.*;
import com.nplohs.market.auth.service.AuthService;
import com.nplohs.market.auth.service.EmailService;
import com.nplohs.market.auth.service.NicknameService;
import com.nplohs.market.common.ratelimit.RateLimiter;
import com.nplohs.market.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService     authService;
    private final EmailService    emailService;
    private final NicknameService nicknameService;
    private final RateLimiter     rateLimiter;

    @Value("${jwt.refresh-expiry-ms:1209600000}")
    private long refreshExpiryMs;

    // 계정별 잠금(AuthService.login)과 별개로, 한 IP가 여러 계정에 대해 동시에
    // 비밀번호 스프레이 공격을 하는 것을 막기 위한 IP 단위 제한. 다만 이 앱은 프론트(Next.js)가
    // /api/*를 서버사이드로 프록시하므로, 리버스 프록시가 X-Forwarded-For를 안 붙여주면
    // 사내 모든 사용자가 사실상 하나의 IP로 잡힐 수 있다 — 그래서 정상적인 출근길 동시 로그인이
    // 걸리지 않도록 한도를 넉넉하게 잡았다. 스크립트로 초당 여러 번 두드리는 것만 막는 정도.
    private static final int    LOGIN_MAX_PER_IP_WINDOW = 30;
    private static final Duration LOGIN_IP_WINDOW = Duration.ofMinutes(1);

    @Value("${jwt.access-expiry-ms:900000}")
    private long accessExpiryMs;

    // 배포 서버가 HTTPS로 전환되면 반드시 true로 설정할 것 (현재는 평문 HTTP 배포라 false가 기본값)
    @Value("${app.cookie-secure:false}")
    private boolean cookieSecure;

    /** POST /api/auth/email/send-code — 이메일 인증코드 발송 */
    @PostMapping("/email/send-code")
    public ResponseEntity<ApiResponse<Void>> sendCode(@RequestBody @Valid EmailCodeRequest request) {
        emailService.sendVerificationCode(request.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("인증코드가 발송되었습니다."));
    }

    /** POST /api/auth/email/verify — 인증코드 확인 */
    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Void>> verifyCode(@RequestBody @Valid EmailVerifyRequest request) {
        emailService.verifyCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ApiResponse.ok("이메일 인증이 완료되었습니다."));
    }

    /** POST /api/auth/password/send-code — 비밀번호 재설정용 인증코드 발송 */
    @PostMapping("/password/send-code")
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetCode(@RequestBody @Valid EmailCodeRequest request) {
        emailService.sendPasswordResetCode(request.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("인증코드가 발송되었습니다."));
    }

    /** POST /api/auth/password/reset — 비밀번호 재설정 */
    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody @Valid PasswordResetRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("비밀번호가 성공적으로 변경되었습니다."));
    }

    /** GET /api/auth/nickname/random — 랜덤 닉네임 생성 */
    @GetMapping("/nickname/random")
    public ResponseEntity<ApiResponse<Map<String, String>>> randomNickname() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("nickname", nicknameService.generate())));
    }

    /** POST /api/auth/signup — 회원가입 (이메일 인증 후 진행) */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signUp(
            @RequestBody @Valid SignUpRequest request,
            HttpServletResponse response) {
        TokenResponse tokenResponse = authService.signUp(request);
        setAuthCookies(response, tokenResponse);
        return ResponseEntity.ok(ApiResponse.ok("회원가입이 완료되었습니다.", sanitizeResponse(tokenResponse)));
    }

    /** POST /api/auth/login */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        if (!rateLimiter.tryAcquire("login-ip:" + clientIp(httpRequest), LOGIN_MAX_PER_IP_WINDOW, LOGIN_IP_WINDOW)) {
            throw new IllegalStateException("로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
        TokenResponse tokenResponse = authService.login(request);
        setAuthCookies(response, tokenResponse);
        return ResponseEntity.ok(ApiResponse.ok(sanitizeResponse(tokenResponse)));
    }

    /** POST /api/auth/refresh */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<?>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        if (!rateLimiter.tryAcquire("refresh-ip:" + clientIp(httpRequest), LOGIN_MAX_PER_IP_WINDOW, LOGIN_IP_WINDOW)) {
            return ResponseEntity.status(429).body(ApiResponse.error("요청이 너무 많습니다. 잠시 후 다시 시도해주세요."));
        }
        if (refreshToken == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("refreshToken이 필요합니다."));
        }
        TokenResponse tokenResponse = authService.refresh(refreshToken);
        setAuthCookies(response, tokenResponse);
        return ResponseEntity.ok(ApiResponse.ok(sanitizeResponse(tokenResponse)));
    }

    /** POST /api/auth/logout */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetails user,
            HttpServletResponse response) {
        if (user != null) {
            authService.logout(user.getUsername());
        }
        clearAuthCookies(response);
        return ResponseEntity.ok(ApiResponse.ok("로그아웃 되었습니다."));
    }

    // ── Cookie Helpers ────────────────────────────────────────────
    // 액세스 토큰도 refresh 토큰과 마찬가지로 httpOnly 쿠키로 내려준다.
    // (프론트가 JS로 읽어 localStorage/document.cookie에 저장하던 방식은 XSS에 그대로 토큰을 내주므로 폐기)
    private void setAuthCookies(HttpServletResponse response, TokenResponse tokenResponse) {
        ResponseCookie accessCookie = ResponseCookie.from("access_token", tokenResponse.getAccessToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(accessExpiryMs / 1000)
                .sameSite("Lax")
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, accessCookie.toString());
        setRefreshTokenCookie(response, tokenResponse.getRefreshToken());
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        // refresh_token은 /api/auth/refresh(및 로그아웃)에서만 쓰이므로, 다른 모든 요청에
        // 실려나가지 않도록 경로를 좁혀 노출 범위를 줄인다.
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(refreshExpiryMs / 1000)
                .sameSite("Lax")
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, accessCookie.toString());

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private TokenResponse sanitizeResponse(TokenResponse tokenResponse) {
        return new TokenResponse(tokenResponse.getAccessToken(), null, tokenResponse.getUser());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
