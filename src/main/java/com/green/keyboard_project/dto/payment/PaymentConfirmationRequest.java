package com.green.keyboard_project.dto.payment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;

/** 개발 주문만 받으며 요청 전체나 외부에서 발급한 결제 키를 로그에 남기지 않습니다. */
public record PaymentConfirmationRequest(
        @NotBlank @Size(max = 200) @Pattern(regexp = "[\\x21-\\x7E]+") String paymentKey,
        @NotBlank @Pattern(regexp = "dev_[a-f0-9]{32}") String orderId,
        @NotNull @Positive Long amount) {
    // 숫자처럼 보이는 문자열이나 소수 금액은 허용하지 않고 JSON 자료형부터 검사합니다.
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

    // 서비스에서 직접 호출되는 경우에도 개발 주문번호·키 형식·양수 금액 검사를 반복합니다.
    public boolean valid() {
        return paymentKey != null && paymentKey.matches("[\\x21-\\x7E]{1,200}")
                && orderId != null && orderId.matches("dev_[a-f0-9]{32}")
                && amount != null && amount > 0;
    }

    // 요청 객체를 출력해도 결제 키가 로그에 남지 않도록 원문을 숨깁니다.
    @Override public String toString() { return "PaymentConfirmationRequest[redacted]"; }
}
