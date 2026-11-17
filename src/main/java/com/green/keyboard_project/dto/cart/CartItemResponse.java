package com.green.keyboard_project.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 장바구니 아이템 응답 DTO (1파일 1클래스)
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class CartItemResponse {
    private Integer cartItemId;
    private Integer userId;
    private Integer productId;
    private String name;
    private Integer price;
    private Integer stockQuantity;
    private Integer quantity;
    private String thumbnailUrl;
}
