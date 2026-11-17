package com.green.keyboard_project.controller;

import com.green.keyboard_project.dto.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 회원 API의 잘못된 JSON만 처리하고 나머지는 기존 공통 예외 처리에 위임합니다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = UserController.class)
public class MemberRequestExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("요청 형식을 확인해 주세요."));
    }
}
