package com.green.keyboard_project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

/** 회원가입 요청에 포함된 기본 배송지의 필수 항목과 길이를 검증합니다. */
public record AddressRequest(
    @NotBlank(message = "우편번호를 입력해 주세요.")
    @Pattern(regexp = "[0-9]{5}", message = "우편번호는 숫자 5자리로 입력해 주세요.") String zonecode,
    @NotBlank(message = "도로명 주소를 입력해 주세요.")
    @Size(max = 255, message = "도로명 주소는 255자 이하로 입력해 주세요.") String roadAddress,
    @NotBlank(message = "상세 주소를 입력해 주세요.")
    @Size(max = 255, message = "상세 주소는 255자 이하로 입력해 주세요.") String detailAddress
) {
}
