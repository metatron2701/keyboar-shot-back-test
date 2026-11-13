package com.green.keyboard_project.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원가입 요청 DTO
 * - 신규 회원 가입 시 필요한 사용자 입력값을 검증하고 전달하는 모델입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSignUpRequest {
    // 계정 이메일 (로그인 ID 겸용, users 테이블 고유값)
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 50, message = "이메일은 최대 50자까지 입력 가능합니다.")
    private String email;

    // 계정 비밀번호 (저장 시 BCrypt 해시 암호화 적용)
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Size(min = 4, max = 100, message = "비밀번호는 최소 4자 이상 입력해 주세요.")
    private String password;

    // 사용자 이름
    @NotBlank(message = "이름은 필수 입력값입니다.")
    @Size(max = 50, message = "이름은 최대 50자까지 입력 가능합니다.")
    private String name;

    // 사용자 연락처 (숫자만 추출하여 최대 11자리 저장)
    @Size(max = 11, message = "전화번호는 최대 11자리의 숫자여야 합니다.")
    private String phoneNumber;
}
