package com.green.keyboard_project.controller;

import com.green.keyboard_project.config.tosspayments.DevPaymentConfirmationConfiguration;
import com.green.keyboard_project.dto.ApiResponse;
import com.green.keyboard_project.dto.payment.*;
import com.green.keyboard_project.service.payment.DevPaymentConfirmationService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Conditional(DevPaymentConfirmationConfiguration.Enabled.class)
public class DevPaymentConfirmationController {
    private final DevPaymentConfirmationService service;
    // 개발 승인 서비스를 연결하며 실제 주문 DB 저장 흐름은 연결하지 않습니다.
    public DevPaymentConfirmationController(DevPaymentConfirmationService service) { this.service = service; }

    // 프론트의 결제 키·개발 주문번호·금액을 검증한 뒤 승인 결과를 반환합니다. 처리 중·미확정은 재확인을 안내하고 응답 캐시는 금지합니다.
    @PostMapping(value = "/api/payments/confirm/none", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<PaymentConfirmationResponse>> confirm(@Valid @RequestBody PaymentConfirmationRequest request) {
        PaymentConfirmationResponse result = service.confirm(request);
        // 처리 중이거나 미확정이면 202, 확인된 결과면 200으로 구분하고 응답의 캐시 저장을 막습니다.
        var builder = ResponseEntity.status(result.pending() ? 202 : 200).cacheControl(CacheControl.noStore());
        if (result.pending()) builder.header(HttpHeaders.RETRY_AFTER, "3");
        return builder.body(ApiResponse.ok(result));
    }
}
