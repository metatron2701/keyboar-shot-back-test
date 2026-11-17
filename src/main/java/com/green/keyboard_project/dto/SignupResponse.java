package com.green.keyboard_project.dto;

/** 회원가입 완료 응답에 화면에 필요한 회원 정보만 전달합니다. */
public record SignupResponse(Long userId, String email, String name, String role, boolean deleted) {
}
