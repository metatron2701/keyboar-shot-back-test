package com.green.keyboard_project.dto.payment;

import java.time.OffsetDateTime;

/** Minimal external response. Keep provider identifiers out of diagnostic strings. */
public record TossPaymentResponse(String paymentKey, String orderId, long totalAmount,
        String currency, String status, String method, OffsetDateTime approvedAt) {
    @Override public String toString() { return "TossPaymentResponse[redacted]"; }
}
