package com.green.keyboard_project.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 결제 승인 완료 요청 DTO
 * - 프론트엔드가 토스 결제창 인증 후 /api/payments/complete 로 전달하는 모델입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCompleteRequest {

    @NotBlank(message = "결제 승인 키(paymentKey)는 필수입니다.")
    private String paymentKey;

    @NotBlank(message = "주문 번호(orderId)는 필수입니다.")
    private String orderId;

    @NotNull(message = "결제 금액(amount)은 필수입니다.")
    private Integer amount;
}
