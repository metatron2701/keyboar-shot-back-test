package com.green.keyboard_project.dto.payment;

import java.time.OffsetDateTime;

/** 허용된 표시 정보만 담은 응답으로 결제 엔티티나 DB 저장 완료 증명이 아닙니다. */
public record PaymentConfirmationResponse(String orderId, long amount, String currency,
        String paymentStatus, boolean completed, String method, OffsetDateTime approvedAt,
        String storage) {
    // 처리 중이거나 미확정인 결과는 완료 아님으로 반환합니다. 메모리 처리 결과이지 결제 DB 저장 증명이 아닙니다.
    public static PaymentConfirmationResponse pending(PaymentConfirmationRequest request, String status) {
        return new PaymentConfirmationResponse(request.orderId(), request.amount(), "KRW",
                status, false, null, null, "MEMORY_ONLY");
    }
    // 컨트롤러가 처리 중·미확정에만 재확인 가능한 응답을 선택하도록 상태를 구분합니다.
    public boolean pending() { return "CONFIRMING".equals(paymentStatus) || "UNKNOWN".equals(paymentStatus); }
}
