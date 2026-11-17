package com.green.keyboard_project.security;

/** 로그인 인증 계층과 회원탈퇴를 연결하는 계약. JwtWithdrawalAuth가 AuthService에 위임합니다. */
public interface WithdrawalAuth {
    record Principal(int userId) { }
    boolean isPasswordMember(int userId);
    /** 회원 탈퇴 트랜잭션 안에서 모든 Refresh Token을 무효화해야 합니다. */
    void revokeRefreshTokens(int userId);
}