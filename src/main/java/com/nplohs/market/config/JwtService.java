package com.nplohs.market.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiry-ms}")
    private long accessExpiryMs;

    @Value("${jwt.refresh-expiry-ms}")
    private long refreshExpiryMs;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String email, Long userId) {
        return build(Map.of("userId", userId, "type", "access"), email, accessExpiryMs);
    }

    public String generateRefreshToken(String email) {
        return build(Map.of("type", "refresh"), email, refreshExpiryMs);
    }

    private String build(Map<String, Object> claims, String subject, long expiryMs) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(getKey())
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * refresh 토큰은 /api/auth/refresh 전용이어야 한다. type 클레임을 확인하지 않으면
     * 탈취된 refresh 토큰(유효기간 14일)이 만료 전까지 계속 access 토큰처럼 통해버려서,
     * refresh 토큰 로테이션으로도 무력화되지 않는 사실상 영구 자격증명이 되어버린다.
     */
    public boolean isAccessToken(String token) {
        try {
            return "access".equals(parseClaims(token).get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
