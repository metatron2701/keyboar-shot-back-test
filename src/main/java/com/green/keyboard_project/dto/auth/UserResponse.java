package com.green.keyboard_project.dto.auth;

import com.green.keyboard_project.entity.User;

// 프론트에 보낼 사용자 정보 (비밀번호 등 민감 정보 제외)
public record UserResponse(int id, String email, String role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getEmail(), user.getRole().name());
    }
}
