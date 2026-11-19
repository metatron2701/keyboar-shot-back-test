package com.green.keyboard_project.service;

import com.green.keyboard_project.dto.SignupRequest;
import com.green.keyboard_project.dto.SignupResponse;
import com.green.keyboard_project.entity.Address;
import com.green.keyboard_project.entity.User;
import com.green.keyboard_project.exceptions.*;
import com.green.keyboard_project.repository.AddressRepository;
import com.green.keyboard_project.repository.UserRepository;
import com.green.keyboard_project.security.WithdrawalAuth;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final AddressRepository addresses;
    private final ObjectProvider<WithdrawalAuth> authentication;
    private final PasswordEncoder passwords;

    public UserService(UserRepository users, AddressRepository addresses,
                       ObjectProvider<WithdrawalAuth> authentication, PasswordEncoder passwords) {
        this.users = users;
        this.addresses = addresses;
        this.authentication = authentication;
        this.passwords = passwords;
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Seoul")); }

    // ===== 회원가입 =====

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) { return !users.existsByEmail(email); }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (users.existsByEmail(request.email())) { throw duplicateEmail(); }
        LocalDateTime time = now();
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwords.encode(request.password()));
        user.setName(request.name());
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(User.RoleType.USER);
        user.setDeleted(false);
        user.setCreatedAt(time);
        user.setUpdatedAt(time);
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMostSpecificCause() instanceof SQLException sql && sql.getErrorCode() == 1062) {
                throw duplicateEmail();
            }
            throw exception;
        }
        Address address = new Address();
        address.setUser(user);
        address.setAddressAlias("기본");
        address.setRecipientName(request.name());
        address.setRecipientPhone(request.phoneNumber());
        address.setPostalCode(request.defaultAddress().zonecode());
        address.setRoadAddress(request.defaultAddress().roadAddress());
        address.setDetailAddress(request.defaultAddress().detailAddress());
        address.setDefault(true);
        address.setCreatedAt(time);
        addresses.saveAndFlush(address);
        return new SignupResponse((long) user.getUserId(), user.getEmail(), user.getName(),
                user.getRole().name(), user.isDeleted());
    }

    private static ResourceConflictException duplicateEmail() {
        return new ResourceConflictException("이미 사용 중인 이메일입니다.");
    }

    // ===== 회원탈퇴 =====

    @Transactional(readOnly = true)
    public boolean verifyPassword(int userId, String password) {
        User user = users.findById(userId).orElseThrow(UserService::inactive);
        requirePasswordMember(user);
        return passwords.matches(password, user.getPassword());
    }

    @Transactional
    public void withdraw(int userId, String password) {
        User user = users.findByIdForWithdrawal(userId).orElseThrow(UserService::inactive);
        WithdrawalAuth auth = requirePasswordMember(user);
        if (!passwords.matches(password, user.getPassword())) {
            throw new AuthorizationFailureException("비밀번호가 일치하지 않습니다.");
        }
        // 32자리 무작위 식별자와 도메인을 합쳐 users.email의 50자 한도를 지킵니다.
        user.setEmail("w-" + UUID.randomUUID().toString().replace("-", "") + "@account.invalid");
        user.setPassword(passwords.encode(UUID.randomUUID().toString()));
        user.setName("탈퇴 회원");
        user.setPhoneNumber(null);
        user.setDeleted(true);
        user.setDeletedAt(now());
        user.setUpdatedAt(user.getDeletedAt());
        auth.revokeRefreshTokens(userId);
    }

    private WithdrawalAuth requirePasswordMember(User user) {
        if (user.isDeleted()) { throw inactive(); }
        WithdrawalAuth auth = authentication.getIfAvailable();
        if (auth == null) { throw new InvalidStateException("회원 인증 연동을 사용할 수 없습니다."); }
        if (!auth.isPasswordMember(user.getUserId())) {
            throw new AuthorizationFailureException("계정에 등록된 인증 수단으로 본인을 확인해 주세요.");
        }
        return auth;
    }

    private static UnauthenticatedException inactive() {
        return new UnauthenticatedException("로그인 정보를 다시 확인해 주세요.");
    }
}
