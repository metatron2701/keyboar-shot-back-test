package com.green.keyboard_project.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 상품 조회 응답 DTO
 * - 클라이언트에게 상품의 상세 및 목록 정보를 전달하는 모델입니다.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProductResponse {
    private int productId;            // 상품 고유 식별자 (PK)
    private String categoryId;        // 카테고리 식별자
    private String name;              // 상품명
    private int basePrice;            // 기본 단가
    private int stockQuantity;        // 보유 재고 수량
    private String thumbnailURL;       // 썸네일 이미지 URL
    private String description;       // 상품 상세 설명
    private boolean isActive;         // 판매 활성화 여부
    private int version;              // 상품 정보 버전
    private LocalDateTime createdAt;  // 상품 등록 일시
    private LocalDateTime updateAt;   // 상품 수정 일시
}
