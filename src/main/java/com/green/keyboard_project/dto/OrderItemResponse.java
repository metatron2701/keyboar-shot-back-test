package com.green.keyboard_project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 주문 품목 응답 DTO
 * - 주문에 포함된 개별 상품의 상세 정보와 주문 당시 금액을 반환하는 모델입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {
    private int orderItemId;       // 주문 상품 식별자 (PK)
    private Integer productId;     // 원본 상품 식별자
    private String productName;    // 주문 당시의 상품명 스냅샷
    private int unitPrice;         // 주문 당시의 상품 단가
    private int quantity;          // 주문 수량
    private int totalPrice;        // 품목 총 금액 (단가 * 수량)
}
