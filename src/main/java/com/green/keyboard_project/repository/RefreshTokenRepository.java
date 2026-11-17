package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.RefreshToken;
import com.green.keyboard_project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {
    Optional<RefreshToken> findByTokenHash(String tokenHash); // 재발급 시 토큰 확인

    void deleteByUser(User user);             // 로그인 시 기존 토큰 삭제 (회원당 1개)

    void deleteByTokenHash(String tokenHash); // 로그아웃 시 해당 토큰 삭제
}
