package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 회원(User) 데이터 접근 리포지토리
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    // 이메일로 활성/비활성 회원 단건 조회
    Optional<User> findByEmail(String email);

    // 회원가입 시 이메일 중복 확인용
    boolean existsByEmail(String email);
}
