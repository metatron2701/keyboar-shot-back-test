package com.green.keyboard_project.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 인증 쿠키 생성·만료 (HttpOnly, SameSite=Lax)
@Component
@RequiredArgsConstructor
public class AuthCookieFactory {

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";

    private final JwtTokenProvider jwtTokenProvider;

    public ResponseCookie accessCookie(String token) {
        return build(ACCESS_COOKIE, token, Duration.ofMillis(jwtTokenProvider.getAccessExpirationMs()));
    }

    public ResponseCookie refreshCookie(String token) {
        return build(REFRESH_COOKIE, token, Duration.ofMillis(jwtTokenProvider.getRefreshExpirationMs()));
    }

    public ResponseCookie expiredAccessCookie() {
        return build(ACCESS_COOKIE, "", Duration.ZERO);
    }

    public ResponseCookie expiredRefreshCookie() {
        return build(REFRESH_COOKIE, "", Duration.ZERO);
    }

    private ResponseCookie build(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)   // 자바스크립트에서 읽을 수 없음 (XSS 방지)
                .sameSite("Lax")  // 다른 사이트 요청에는 미전송 (CSRF 방지)
                .secure(false)    // 로컬(http) 개발용. 배포(https) 시 true
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
