package com.green.keyboard_project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 주문 생성 요청 DTO
 * - 클라이언트가 주문을 생성할 때 전달하는 데이터 모델입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreateRequest {
    // 주문자 이름 (미입력 시 회원 정보의 이름을 사용)
    private String ordererName;

    // 주문자 연락처 (미입력 시 회원 정보의 연락처를 사용, 최대 11자리)
    private String ordererPhone;

    // 주문자 계정 이메일 (회원 식별용 필수값)
    @NotBlank(message = "주문자 이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String ordererEmail;

    // 주문할 상품 및 수량 목록
    @NotEmpty(message = "주문 상품 목록은 비어 있을 수 없습니다.")
    @Valid
    private List<OrderItemRequest> items;
}
