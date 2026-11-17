package com.green.keyboard_project.dto.payment;

import java.time.OffsetDateTime;

/** Whitelist response, not a Payment entity and not proof of DB persistence. */
public record PaymentConfirmationResponse(String orderId, long amount, String currency,
        String paymentStatus, boolean completed, String method, OffsetDateTime approvedAt,
        String storage) {
    public static PaymentConfirmationResponse pending(PaymentConfirmationRequest request, String status) {
        return new PaymentConfirmationResponse(request.orderId(), request.amount(), "KRW",
                status, false, null, null, "MEMORY_ONLY");
    }
    public boolean pending() { return "CONFIRMING".equals(paymentStatus) || "UNKNOWN".equals(paymentStatus); }
}
