
package com.green.keyboard_project.controller;

import com.green.keyboard_project.dto.*;
import com.green.keyboard_project.service.UserService;
import com.green.keyboard_project.exceptions.UnauthenticatedException;
import com.green.keyboard_project.security.AuthCookieFactory;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {

    private final UserService users;
    private final AuthCookieFactory cookies;

    public UserController(
            UserService users,
            AuthCookieFactory cookies
    ) {
        this.users = users;
        this.cookies = cookies;
    }

    // 회원가입
    @PostMapping("/api/users/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return ApiResponse.ok(users.signup(request));
    }

    // 이메일 중복 확인
    @GetMapping("/api/auth/check-email")
    public ApiResponse<EmailAvailabilityResponse> checkEmail(
            @Valid @ModelAttribute SignupEmailRequest request
    ) {
        return ApiResponse.ok(
                new EmailAvailabilityResponse(
                        users.isEmailAvailable(request.email())
                )
        );
    }

    // 로그인한 회원 번호 확인
    private int userId(Integer userId) {
        if (userId == null) {
            throw new UnauthenticatedException(
                    "로그인 정보를 다시 확인해 주세요."
            );
        }

        return userId;
    }

    // 회원탈퇴 전 비밀번호 재확인
    @PostMapping("/api/users/me/verify-password")
    public ApiResponse<WithdrawalPasswordResponse> verifyPassword(
            @AuthenticationPrincipal Integer userId,
            @Valid @RequestBody WithdrawalPasswordRequest request
    ) {
        return ApiResponse.ok(
                new WithdrawalPasswordResponse(
                        users.verifyPassword(
                                userId(userId),
                                request.password()
                        )
                )
        );
    }

    // 회원탈퇴
    @DeleteMapping("/api/users/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal Integer userId,
            @Valid @RequestBody WithdrawalPasswordRequest request
    ) {
        // 회원탈퇴 처리
        users.withdraw(
                userId(userId),
                request.password()
        );

        // 탈퇴 성공 시 Access / Refresh 쿠키 만료
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.expiredAccessCookie().toString()
                )
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.expiredRefreshCookie().toString()
                )
                .body(ApiResponse.ok());
    }
}
