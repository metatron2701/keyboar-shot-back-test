package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email); // 로그인
    boolean existsByEmail(String email);      // 회원가입 이메일 중복 확인
}
