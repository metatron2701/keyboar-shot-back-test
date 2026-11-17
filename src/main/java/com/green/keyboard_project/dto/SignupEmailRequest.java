package com.green.keyboard_project.dto;

import static com.green.keyboard_project.dto.SignupRequest.EMAIL_FORMAT_MESSAGE;
import static com.green.keyboard_project.dto.SignupRequest.EMAIL_PATTERN;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이메일 중복 확인 요청에 회원가입과 동일한 이메일 형식 규칙을 적용합니다. */
public record SignupEmailRequest(
    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(regexp = EMAIL_PATTERN, message = EMAIL_FORMAT_MESSAGE)
    @Size(max = 50, message = EMAIL_FORMAT_MESSAGE) String email
) {
}
