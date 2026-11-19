package com.green.keyboard_project.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;

/** 탈퇴 전 재확인과 최종 요청에서 현재 비밀번호를 받습니다. */
public record WithdrawalPasswordRequest(@NotBlank String password) {

    // BCrypt 한도는 UTF-8 바이트로 검사하고, 빈 값은 @NotBlank에서 처리합니다.
    @AssertTrue(message = "비밀번호 길이를 확인해 주세요.")
    public boolean isPasswordLengthValid() {
        return password == null || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
