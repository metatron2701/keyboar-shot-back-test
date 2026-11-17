package com.green.keyboard_project.dto.auth;

// 로그인·/me 응답의 data 부분: { user: { id, email, role } }
public record AuthUserResponse(UserResponse user) {
}
