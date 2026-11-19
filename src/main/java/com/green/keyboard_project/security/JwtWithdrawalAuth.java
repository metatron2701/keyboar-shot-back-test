package com.green.keyboard_project.security;

import com.green.keyboard_project.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** WithdrawalAuth 구현체: 회원탈퇴가 필요한 인증 기능을 로그인 쪽 AuthService에 위임합니다. */
@Component
@RequiredArgsConstructor
public class JwtWithdrawalAuth implements WithdrawalAuth {

    private final AuthService authService;

    @Override
    public boolean isPasswordMember(int userId) {
        return authService.isPasswordMember(userId);
    }

    @Override
    public void revokeRefreshTokens(int userId) {
        authService.revokeAllRefreshTokens(userId);
    }
}