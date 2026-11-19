package com.green.keyboard_project.controller;

import com.green.keyboard_project.dto.ApiResponse;
import com.green.keyboard_project.dto.order.OrderResponse;
import com.green.keyboard_project.dto.payment.PaymentCompleteRequest;
import com.green.keyboard_project.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 결제 승인 및 취소 컨트롤러
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final OrderService orderService;

    /**
     * 1. 결제 최종 승인 요청 (POST /api/payments/complete)
     * - 프론트엔드 성공 페이지에서 paymentKey, orderId, amount를 받아 토스 서버 승인 후 DB 상태를 갱신합니다.
     */
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<OrderResponse>> completePayment(
            @Valid @RequestBody PaymentCompleteRequest request
    ) {
        OrderResponse response = orderService.completePayment(
                request.getOrderId(),
                request.getPaymentKey(),
                request.getAmount()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 2. 결제 실패/취소 통지 (POST /api/payments/fail)
     * - 결제 실패 또는 사용자가 결제 취소 시 주문 상태를 CANCELLED로 변경하고 재고를 원복합니다.
     */
    @PostMapping("/fail")
    public ResponseEntity<ApiResponse<String>> failPayment(
            @RequestParam("orderId") String orderId,
            @RequestParam(name = "message", defaultValue = "결제 실패") String message
    ) {
        orderService.failPayment(orderId, message);
        return ResponseEntity.ok(ApiResponse.ok("결제 실패 처리가 완료되었습니다."));
    }
}
