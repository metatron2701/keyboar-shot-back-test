package com.green.keyboard_project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 조회 응답 DTO
 * - 주문 기본 정보와 하위 품목 목록을 담아 반환하는 모델입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private int orderId;                   // 주문 식별자 (PK)
    private String orderNumber;            // 외부 공개용 고유 주문번호 (ORD-...)
    private String orderStatus;            // 주문 상태 (PENDING, ORDERED, CANCELLED)
    private String ordererName;            // 주문자 이름
    private String ordererPhone;           // 주문자 연락처
    private String ordererEmail;           // 주문자 이메일

    private int totalProductAmount;        // 총 상품 금액
    private int deliveryFee;               // 배송비
    private int finalPayAmount;            // 최종 결제 금액 (상품금액 + 배송비)

    private LocalDateTime orderedAt;       // 주문 접수 일시
    private LocalDateTime createdAt;       // 주문 생성 일시

    private List<OrderItemResponse> items; // 주문 품목 목록
}
