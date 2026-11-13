package com.green.keyboard_project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 장바구니 아이템 요청 및 응답 DTO (1파일 1클래스)
 * - 원시 타입(int) 대신 래퍼 클래스(Integer)를 사용하여 요청 시 생략된 필드가 null이어도 역직렬화 에러가 발생하지 않도록 방어합니다.
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
