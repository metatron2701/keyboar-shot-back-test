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

/** 승인 API에만 적용하며 입력 객체·외부 예외·검증 실패값의 원문을 기록하지 않습니다. */
@Order(-10)
@RestControllerAdvice(assignableTypes = DevPaymentConfirmationController.class)
@Conditional(DevPaymentConfirmationConfiguration.Enabled.class)
public class DevPaymentConfirmationExceptionHandler {
    public record ErrorData(String code, String providerCode) {}
    // 승인 서비스가 구분한 오류 상태와 안전한 코드만 공통 응답으로 전달합니다.
    @ExceptionHandler(PaymentConfirmationException.class)
    public ResponseEntity<ApiResponse<ErrorData>> payment(PaymentConfirmationException error) {
        return response(error.status(), error.code(), error.getMessage(), error.providerCode());
    }
    // 잘못된 JSON이나 입력 검증 오류는 원본 입력값 없이 요청 오류로 안내합니다.
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<ErrorData>> invalid(Exception ignored) {
        return response(400, "INVALID_CONFIRM_REQUEST", "승인 요청의 필수값과 형식을 확인해 주세요.");
    }
    // JSON이 아닌 요청은 승인 처리 전에 잘못된 요청 형식으로 응답합니다.
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorData>> media(Exception ignored) {
        return response(415, "INVALID_CONFIRM_CONTENT_TYPE", "JSON 요청만 사용할 수 있습니다.");
    }
    // 예상하지 못한 오류도 상세 예외를 노출하거나 승인 실패로 단정하지 않고 상태 확인을 안내합니다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorData>> unexpected(Exception ignored) {
        return response(500, "PAYMENT_CONFIRM_INTERNAL_ERROR", "승인 처리 상태를 확인하지 못했습니다.");
    }
    // 외부 오류 코드가 없는 경우에도 같은 응답 생성 경로를 사용합니다.
    private ResponseEntity<ApiResponse<ErrorData>> response(int status, String code, String message) {
        return response(status, code, message, null);
    }
    // 선별된 오류 정보만 본문에 넣고 캐시를 막아 다른 결제 요청에 이전 오류가 재사용되지 않게 합니다.
    private ResponseEntity<ApiResponse<ErrorData>> response(int status, String code, String message, String providerCode) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(ApiResponse.<ErrorData>builder().success(false).message(message).data(new ErrorData(code, providerCode)).build());
    }
}
