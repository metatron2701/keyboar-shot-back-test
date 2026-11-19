package com.green.keyboard_project.dto.payment;

import java.time.OffsetDateTime;

/** 외부 승인 응답의 필요한 항목만 담고 진단용 문자열에는 결제 식별자를 노출하지 않습니다. */
// 토스가 보낸 결과 중 주문·금액·상태를 대조하는 데 필요한 항목만 담고 내부 서비스가 검증하도록 전달합니다.
public record TossPaymentResponse(String paymentKey, String orderId, long totalAmount,
        String currency, String status, String method, OffsetDateTime approvedAt) {
    // 외부 응답을 진단용으로 출력하더라도 결제 키는 노출하지 않습니다.
    @Override public String toString() { return "TossPaymentResponse[redacted]"; }
}
