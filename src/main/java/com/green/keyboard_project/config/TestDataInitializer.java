package com.green.keyboard_project.config;

import com.green.keyboard_project.entity.User;
import com.green.keyboard_project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// 로그인 테스트용 회원 3명 생성 (이미 있으면 건너뜀)
// local 프로필에서만 실행된다 (각자 application-secret.properties에 spring.profiles.active=local)
// TODO: 회원가입 기능 완성 후 삭제 검토
@Profile("local")
@Component
@RequiredArgsConstructor
public class TestDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String pw = passwordEncoder.encode("test1234!");
        createIfAbsent("user@test.com", pw, "일반회원", User.RoleType.USER, false);
        createIfAbsent("admin@test.com", pw, "관리자", User.RoleType.ADMIN, false);
        createIfAbsent("withdrawn@test.com", pw, "탈퇴회원", User.RoleType.USER, true);
    }

    private void createIfAbsent(String email, String encodedPw, String name, User.RoleType role, boolean deleted) {
        if (userRepository.findByEmail(email).isPresent()) return;
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setEmail(email);
        user.setPassword(encodedPw);
        user.setName(name);
        user.setRole(role);
        user.setDeleted(deleted);
        user.setDeletedAt(deleted ? now : null);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }
}
