package com.green.keyboard_project.controller;

import com.green.keyboard_project.dto.*;
import com.green.keyboard_project.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users = users; }
    @PostMapping("/api/users/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(users.signup(request));
    }
    @GetMapping("/api/auth/check-email")
    public ApiResponse<EmailAvailabilityResponse> checkEmail(@Valid @ModelAttribute SignupEmailRequest request) {
        return ApiResponse.ok(new EmailAvailabilityResponse(users.isEmailAvailable(request.email())));
    }
}
