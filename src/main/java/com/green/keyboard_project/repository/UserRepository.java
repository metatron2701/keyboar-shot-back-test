package com.green.keyboard_project.repository;

import com.green.keyboard_project.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email); // 로그인
    boolean existsByEmail(String email);      // 회원가입 이메일 중복 확인

    // 회원탈퇴: 탈퇴 처리 중 같은 회원 행을 다른 요청이 수정하지 못하도록 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :id")
    Optional<User> findByIdForWithdrawal(@Param("id") int id);
}
