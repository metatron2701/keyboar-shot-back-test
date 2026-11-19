package com.green.keyboard_project.controller;

import com.green.keyboard_project.dto.ApiResponse;
import com.green.keyboard_project.dto.auth.AuthUserResponse;
import com.green.keyboard_project.dto.auth.LoginRequest;
import com.green.keyboard_project.dto.auth.UserResponse;
import com.green.keyboard_project.entity.Address;
import com.green.keyboard_project.exceptions.UnauthenticatedException;
import com.green.keyboard_project.repository.AddressRepository;
import com.green.keyboard_project.security.AuthCookieFactory;
import com.green.keyboard_project.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory cookieFactory;
    private final AddressRepository addressRepository;

    // 로그인: 성공 시 토큰 2개를 쿠키로, 사용자 정보를 본문(data.user)으로 응답
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthUserResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.email(), request.password());
        Address defaultAddress = addressRepository.findByUser_UserIdAndIsDefaultTrue(result.user().getUserId()).orElse(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.accessCookie(result.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refreshCookie(result.refreshToken()).toString())
                .body(ApiResponse.ok(new AuthUserResponse(UserResponse.from(result.user(), defaultAddress))));
    }

    // 로그인한 사용자 정보 (새로고침 시 로그인 상태 복구용)
    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me(@AuthenticationPrincipal Integer userId) {
        if (userId == null) throw new UnauthenticatedException(AuthService.LOGIN_REQUIRED);
        Address defaultAddress = addressRepository.findByUser_UserIdAndIsDefaultTrue(userId).orElse(null);
        return ApiResponse.ok(new AuthUserResponse(UserResponse.from(authService.getActiveUser(userId), defaultAddress)));
    }

    // 로그아웃: DB 토큰 삭제 결과와 관계없이 쿠키는 만료
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookieFactory.REFRESH_COOKIE, required = false) String refreshToken) {
        try {
            authService.logout(refreshToken);
        } catch (Exception e) {
            log.warn("Refresh Token 삭제 실패", e);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expiredAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expiredRefreshCookie().toString())
                .build();
    }

    // 재발급: 성공 시 새 Access Token 쿠키, 실패 시 401 + 쿠키 만료
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            @CookieValue(name = AuthCookieFactory.REFRESH_COOKIE, required = false) String refreshToken) {
        try {
            String accessToken = authService.refresh(refreshToken);
            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, cookieFactory.accessCookie(accessToken).toString())
                    .build();
        } catch (UnauthenticatedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookieFactory.expiredAccessCookie().toString())
                    .header(HttpHeaders.SET_COOKIE, cookieFactory.expiredRefreshCookie().toString())
                    .body(ApiResponse.fail(e.getMessage()));
        }
    }
}
