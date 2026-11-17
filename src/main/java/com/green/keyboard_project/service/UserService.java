package com.green.keyboard_project.service;

import com.green.keyboard_project.dto.SignupRequest;
import com.green.keyboard_project.dto.SignupResponse;
import com.green.keyboard_project.entity.Address;
import com.green.keyboard_project.entity.User;
import com.green.keyboard_project.exceptions.*;
import com.green.keyboard_project.repository.AddressRepository;
import com.green.keyboard_project.repository.UserRepository;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final AddressRepository addresses;
    private final PasswordEncoder passwords;

    public UserService(UserRepository users, AddressRepository addresses, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
        this.addresses = addresses;
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Seoul")); }

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
}