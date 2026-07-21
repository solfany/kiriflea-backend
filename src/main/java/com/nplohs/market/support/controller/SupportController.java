package com.nplohs.market.support.controller;

import com.nplohs.market.auth.service.EmailService;
import com.nplohs.market.common.ratelimit.RateLimiter;
import com.nplohs.market.support.dto.ContactRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final EmailService emailService;
    private final RateLimiter  rateLimiter;

    @PostMapping("/contact")
    public ResponseEntity<Void> submitContact(
            @RequestBody @Valid ContactRequest request,
            @AuthenticationPrincipal UserDetails user) {
        if (!rateLimiter.tryAcquire("support:contact:" + user.getUsername(), 3, Duration.ofMinutes(10))) {
            throw new IllegalStateException("문의 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
        emailService.sendDeveloperContactEmail(request.getEmail(), request.getContent());
        return ResponseEntity.ok().build();
    }
}
