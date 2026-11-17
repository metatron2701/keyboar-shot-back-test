package com.green.keyboard_project.controller;

import com.green.keyboard_project.config.tosspayments.DevPaymentConfirmationConfiguration;
import com.green.keyboard_project.dto.ApiResponse;
import com.green.keyboard_project.exceptions.PaymentConfirmationException;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/** Scoped to payment confirmation; no raw DTO, provider exception or binding value logging. */
@Order(-10)
@RestControllerAdvice(assignableTypes = DevPaymentConfirmationController.class)
@Conditional(DevPaymentConfirmationConfiguration.Enabled.class)
public class DevPaymentConfirmationExceptionHandler {
    public record ErrorData(String code, String providerCode) {}
    @ExceptionHandler(PaymentConfirmationException.class)
    public ResponseEntity<ApiResponse<ErrorData>> payment(PaymentConfirmationException error) {
        return response(error.status(), error.code(), error.getMessage(), error.providerCode());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<ErrorData>> invalid(Exception ignored) {
        return response(400, "INVALID_CONFIRM_REQUEST", "승인 요청의 필수값과 형식을 확인해 주세요.");
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorData>> media(Exception ignored) {
        return response(415, "INVALID_CONFIRM_CONTENT_TYPE", "JSON 요청만 사용할 수 있습니다.");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorData>> unexpected(Exception ignored) {
        return response(500, "PAYMENT_CONFIRM_INTERNAL_ERROR", "승인 처리 상태를 확인하지 못했습니다.");
    }
    private ResponseEntity<ApiResponse<ErrorData>> response(int status, String code, String message) {
        return response(status, code, message, null);
    }
    private ResponseEntity<ApiResponse<ErrorData>> response(int status, String code, String message, String providerCode) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ApiResponse.<ErrorData>builder().success(false).message(message).data(new ErrorData(code, providerCode)).build());
    }
}
