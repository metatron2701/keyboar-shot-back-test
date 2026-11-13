package com.green.keyboard_project.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// 모든 요청에서 쿠키의 Access Token을 꺼내 검증하고, 통과하면 "회원 N번의 요청"으로 표시
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = findCookie(request, AuthCookieFactory.ACCESS_COOKIE);
        if (token != null && !token.isBlank()) {
            try {
                Claims claims = jwtTokenProvider.parse(token);
                if (JwtTokenProvider.TYPE_ACCESS.equals(claims.get("type", String.class))) {
                    Integer userId = Integer.valueOf(claims.getSubject());
                    String role = claims.get("role", String.class);
                    var auth = new UsernamePasswordAuthenticationToken(
                            userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // 만료·위조 토큰: 인증 없이 진행 → 보호된 API는 401
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private String findCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
