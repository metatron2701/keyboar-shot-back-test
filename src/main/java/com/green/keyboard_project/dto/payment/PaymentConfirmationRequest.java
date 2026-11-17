package com.green.keyboard_project.dto.payment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

/** Development orders only. Never log the request or the provider-issued key. */
public record PaymentConfirmationRequest(
        @NotBlank @Size(max = 200) @Pattern(regexp = "[\\x21-\\x7E]+") String paymentKey,
        @NotBlank @Pattern(regexp = "dev_[a-f0-9]{32}") String orderId,
        @NotNull @Positive Long amount) {
    @JsonCreator
    public static PaymentConfirmationRequest fromJson(
            @JsonProperty("paymentKey") JsonNode key,
            @JsonProperty("orderId") JsonNode order,
            @JsonProperty("amount") JsonNode amount) {
        if ((key != null && !key.isNull() && !key.isTextual())
                || (order != null && !order.isNull() && !order.isTextual())
                || (amount != null && !amount.isNull()
                    && (!amount.isIntegralNumber() || !amount.canConvertToLong()))) {
            throw new IllegalArgumentException("Invalid confirmation field types");
        }
        return new PaymentConfirmationRequest(
                key == null || key.isNull() ? null : key.asText(),
                order == null || order.isNull() ? null : order.asText(),
                amount == null || amount.isNull() ? null : amount.longValue());
    }

    public boolean valid() {
        return paymentKey != null && paymentKey.matches("[\\x21-\\x7E]{1,200}")
                && orderId != null && orderId.matches("dev_[a-f0-9]{32}")
                && amount != null && amount > 0;
    }

    @Override public String toString() { return "PaymentConfirmationRequest[redacted]"; }
}
