package com.green.keyboard_project.service;

import com.green.keyboard_project.dto.UserLoginRequest;
import com.green.keyboard_project.dto.UserResponse;
import com.green.keyboard_project.dto.UserSignUpRequest;
import com.green.keyboard_project.entity.User;
import com.green.keyboard_project.exceptions.InvalidStateException;
import com.green.keyboard_project.exceptions.ResourceNotFountException;
import com.green.keyboard_project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;     // 회원 DB 접근
    private final PasswordEncoder passwordEncoder;   // BCrypt 비밀번호 해싱 및 검증

    /**
     * 회원가입
     * - 이메일 중복 확인 -> 비밀번호 BCrypt 해싱 -> 회원 엔티티 생성 및 DB 저장
     */
    @Transactional
    public UserResponse signUp(UserSignUpRequest request) {
        // 1. 이메일 중복 체크 (users 테이블의 email unique 제약조건 방어)
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new InvalidStateException("이미 사용 중인 이메일입니다.");
        }

        // 2. 전화번호 숫자만 추출 및 11자리 제한
        String cleanPhone = null;
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            cleanPhone = request.getPhoneNumber().replaceAll("[^0-9]", "");
            if (cleanPhone.length() > 11) {
                cleanPhone = cleanPhone.substring(0, 11);
            }
        }

        // 3. 신규 회원 엔티티 구성
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword())); // 평문 비밀번호를 암호화하여 저장
        user.setName(request.getName());
        user.setPhoneNumber(cleanPhone);
        user.setRole(User.RoleType.USER); // 기본 일반 사용자 권한 부여
        user.setDeleted(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        // 4. DB 저장 후 응답 DTO 반환
        User savedUser = userRepository.save(user);
        return toUserResponse(savedUser);
    }

    /**
     * 로그인
     * - 이메일 존재 여부 -> 탈퇴 여부 -> 암호화된 비밀번호 일치 확인
     */
    @Transactional(readOnly = true)
    public UserResponse login(UserLoginRequest request) {
        // 1. 이메일로 회원 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidStateException("이메일 또는 비밀번호가 올바르지 않습니다."));

        // 2. 탈퇴 회원 여부 확인
        if (user.isDeleted()) {
            throw new InvalidStateException("탈퇴 처리된 회원입니다.");
        }

        // 3. 비밀번호 해시 일치 여부 검증 (평문과 BCrypt 해시 비교)
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidStateException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return toUserResponse(user);
    }

    /**
     * 사용자 단건 조회 (PK 기준)
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFountException("사용자를 찾을 수 없습니다. (ID: " + userId + ")"));
        return toUserResponse(user);
    }

    /**
     * 엔티티(User) -> 응답 DTO(UserResponse) 변환 헬퍼 메서드 (비밀번호는 제외)
     */
    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
