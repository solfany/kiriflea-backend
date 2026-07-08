package com.nplohs.market.auth.controller;

import com.nplohs.market.auth.dto.*;
import com.nplohs.market.auth.service.AuthService;
import com.nplohs.market.auth.service.EmailService;
import com.nplohs.market.auth.service.NicknameService;
import com.nplohs.market.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<ApiResponse<TokenResponse>> signUp(@RequestBody @Valid SignUpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("회원가입이 완료되었습니다.", authService.signUp(request)));
    }

    /** POST /api/auth/login */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    /** POST /api/auth/refresh */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<?>> refresh(@RequestBody Map<String, String> body) {
        String token = body.get("refreshToken");
        if (token == null) return ResponseEntity.badRequest().body(ApiResponse.error("refreshToken이 필요합니다."));
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(token)));
    }

    /** POST /api/auth/logout */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal UserDetails user) {
        authService.logout(user.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("로그아웃 되었습니다."));
    }
}
