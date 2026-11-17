package com.green.keyboard_project.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 로그인 요청 데이터 (서버에서도 필수값·이메일 형식 재검증)
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
