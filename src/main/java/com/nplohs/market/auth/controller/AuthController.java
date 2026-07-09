package com.nplohs.market.auth.controller;

import com.nplohs.market.auth.dto.*;
import com.nplohs.market.auth.service.AuthService;
import com.nplohs.market.auth.service.EmailService;
import com.nplohs.market.auth.service.NicknameService;
import com.nplohs.market.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService     authService;
    private final EmailService    emailService;
    private final NicknameService nicknameService;

    @Value("${jwt.refresh-expiry-ms:1209600000}")
    private long refreshExpiryMs;

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
        setRefreshTokenCookie(response, tokenResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok("회원가입이 완료되었습니다.", sanitizeResponse(tokenResponse)));
    }

    /** POST /api/auth/login */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletResponse response) {
        TokenResponse tokenResponse = authService.login(request);
        setRefreshTokenCookie(response, tokenResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(sanitizeResponse(tokenResponse)));
    }

    /** POST /api/auth/refresh */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<?>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("refreshToken이 필요합니다."));
        }
        TokenResponse tokenResponse = authService.refresh(refreshToken);
        setRefreshTokenCookie(response, tokenResponse.getRefreshToken());
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
        clearRefreshTokenCookie(response);
        return ResponseEntity.ok(ApiResponse.ok("로그아웃 되었습니다."));
    }

    // ── Cookie Helpers ────────────────────────────────────────────
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(false) // local dev over http, set true for https production
                .path("/")
                .maxAge(refreshExpiryMs / 1000)
                .sameSite("Lax")
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private TokenResponse sanitizeResponse(TokenResponse tokenResponse) {
        return new TokenResponse(tokenResponse.getAccessToken(), null, tokenResponse.getUser());
    }
}
