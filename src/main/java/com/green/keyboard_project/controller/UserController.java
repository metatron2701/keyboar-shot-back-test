package com.green.keyboard_project.controller;

import com.green.keyboard_project.dto.ApiResponse;
import com.green.keyboard_project.dto.UserLoginRequest;
import com.green.keyboard_project.dto.UserResponse;
import com.green.keyboard_project.dto.UserSignUpRequest;
import com.green.keyboard_project.entity.User;
import com.green.keyboard_project.security.AuthCookieFactory;
import com.green.keyboard_project.security.JwtTokenProvider;
import com.green.keyboard_project.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users") // 회원 관련 공통 API 엔드포인트 접두사
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;             // 회원 비즈니스 로직 주입
    private final JwtTokenProvider jwtTokenProvider;   // 사용자 작성 토큰 생성기 주입
    private final AuthCookieFactory authCookieFactory; // 사용자 작성 쿠키 팩토리 주입

    /**
     * 1. 회원가입 (POST /api/users/signup)
     * - 이메일, 비밀번호, 이름, 연락처를 전달받아 신규 회원을 등록합니다.
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signUp(
            @Valid @RequestBody UserSignUpRequest request
    ) {
        UserResponse response = userService.signUp(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 2. 로그인 (POST /api/users/login)
     * - 이메일과 비밀번호를 검증하여 로그인 성공 시 회원 정보 반환 및 refresh_token, access_token 쿠키 발급
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(
            @Valid @RequestBody UserLoginRequest request
    ) {
        // 1. 회원 인증 검증
        UserResponse response = userService.login(request);

        // 2. 작성해주신 JwtTokenProvider로 리프레시 토큰 및 액세스 토큰 생성
        String refreshToken = jwtTokenProvider.createRefreshToken(response.getUserId());
        String accessToken = jwtTokenProvider.createAccessToken(
                response.getUserId(),
                User.RoleType.valueOf(response.getRole())
        );

        // 3. 작성해주신 AuthCookieFactory로 HttpOnly 쿠키 생성
        ResponseCookie refreshCookie = authCookieFactory.refreshCookie(refreshToken);
        ResponseCookie accessCookie = authCookieFactory.accessCookie(accessToken);

        // 4. Set-Cookie 헤더를 실어 응답 (refresh_token, access_token 동봉)
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .body(ApiResponse.ok(response));
    }

    /**
     * 3. 회원 상세 조회 (GET /api/users/{userId})
     * - 특정 회원의 식별자(userId)로 회원 정보를 조회합니다.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable int userId
    ) {
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
