package com.green.keyboard_project.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 장바구니 상품 수량 변경 요청 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CartQuantityUpdateRequest {
    private Integer quantity;
}
