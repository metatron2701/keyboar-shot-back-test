package com.green.keyboard_project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 회원 정보 응답 DTO
 * - 비밀번호 등 민감 정보를 제외하고 안전하게 회원 정보를 반환하는 모델입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private int userId;              // 회원 고유 식별자 (PK)
    private String email;            // 계정 이메일
    private String name;             // 사용자 이름
    private String phoneNumber;      // 사용자 연락처
    private String role;             // 회원 권한 (USER, ADMIN)
    private LocalDateTime createdAt; // 가입 일시
}
