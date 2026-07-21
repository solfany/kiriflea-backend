package com.nplohs.market.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = null;
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            token = auth.substring(7);
        } else if (request.getCookies() != null) {
            for (var c : request.getCookies()) {
                if ("access_token".equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }
        if (token == null && request.getRequestURI().startsWith("/ws")) {
            // SockJS/STOMP's HTTP-fallback transports don't always carry cookies on every
            // sub-request, so the WS handshake path alone may still fall back to a query
            // param. Restricted to /ws — everywhere else, a token in the URL would leak
            // into access logs and Referer headers.
            token = request.getParameter("token");
        }

        // refresh 토큰은 /api/auth/refresh 전용 자격증명이다. type 클레임을 확인하지 않으면
        // 탈취된 refresh 토큰(14일 유효)이 만료 전까지 모든 API에서 access 토큰처럼 동작해버려서
        // refresh-token 로테이션으로도 무력화되지 않는 사실상 영구 인증 수단이 되어버린다.
        if (token == null || !jwtService.isValid(token) || !jwtService.isAccessToken(token)) {
            chain.doFilter(request, response);
            return;
        }


        String email = jwtService.extractEmail(token);
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails ud = userDetailsService.loadUserByUsername(email);
            var authToken = new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        chain.doFilter(request, response);
    }
}
