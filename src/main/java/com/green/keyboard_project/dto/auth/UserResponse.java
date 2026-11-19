package com.green.keyboard_project.dto.auth;

import com.green.keyboard_project.entity.Address;
import com.green.keyboard_project.entity.User;

// 프론트에 보낼 사용자 정보 (비밀번호 등 민감 정보 제외)
public record UserResponse(
        int id,
        String email,
        String role,
        String name,
        String phoneNumber,
        String postalCode,
        String roadAddress,
        String detailAddress) {

    public static UserResponse from(User user) {
        return from(user, null);
    }

    public static UserResponse from(User user, Address defaultAddress) {
        return new UserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getRole().name(),
                user.getName(),
                user.getPhoneNumber(),
                defaultAddress != null ? defaultAddress.getPostalCode() : null,
                defaultAddress != null ? defaultAddress.getRoadAddress() : null,
                defaultAddress != null ? defaultAddress.getDetailAddress() : null
        );
    }
}
