package com.green.keyboard_project.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

/** 가입 요청의 데이터를 받고 형식·필수 입력을 검증합니다. 이메일 중복은 서비스에서 확인합니다. */
public record SignupRequest(
    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(regexp = EMAIL_PATTERN, message = EMAIL_FORMAT_MESSAGE)
    @Size(max = 50, message = EMAIL_FORMAT_MESSAGE) String email,
    @NotBlank(message = "비밀번호를 입력해 주세요.") String password,
    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.") String name,
    @NotBlank(message = "연락처를 입력해 주세요.")
    @Pattern(regexp = "010[0-9]{8}", message = "010으로 시작하는 11자리 번호를 입력해 주세요.") String phoneNumber,
    @NotNull(message = "기본 배송지를 입력해 주세요.") @Valid AddressRequest defaultAddress,
    @NotNull(message = TERMS_REQUIRED_MESSAGE) @Valid TermsConsent termsConsent
) {
    public static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    public static final String EMAIL_FORMAT_MESSAGE = "올바른 이메일 형식으로 입력해 주세요.";
    static final String TERMS_REQUIRED_MESSAGE = "필수 약관에 모두 동의해 주세요.";

    // 기존 중첩 배송지 형식과 팀 화면의 평면 형식을 하나의 DTO로 맞춰 서비스의 저장 흐름을 공유합니다.
    // 두 형식이 함께 오면 어느 값을 쓸지 임의로 선택하지 않고 잘못된 요청으로 거부합니다.
    @JsonCreator
    public static SignupRequest fromJson(
        @JsonProperty("email") String email,
        @JsonProperty("password") String password,
        @JsonProperty("name") String name,
        @JsonProperty("phoneNumber") String phoneNumber,
        @JsonProperty("defaultAddress") AddressRequest defaultAddress,
        @JsonProperty("termsConsent") TermsConsent termsConsent,
        @JsonProperty("phone") String phone,
        @JsonProperty("postalCode") String postalCode,
        @JsonProperty("address") String address,
        @JsonProperty("detailAddress") String detailAddress
    ) {
        boolean flatAddress = postalCode != null || address != null || detailAddress != null;
        if ((phoneNumber != null && phone != null) || (defaultAddress != null && flatAddress)) {
            throw new IllegalArgumentException("Conflicting signup fields");
        }
        if (flatAddress) {
            defaultAddress = new AddressRequest(postalCode, address, detailAddress);
        }
        return new SignupRequest(email, password, name, phoneNumber != null ? phoneNumber : phone,
            defaultAddress, termsConsent);
    }

    // 화면에서 보는 글자 수와 BCrypt에 전달할 UTF-8 바이트 수를 각각 확인합니다.
    // 같은 글자 수라도 한글·이모지는 바이트 수가 달라지므로 BCrypt의 72바이트 한도를 별도로 지킵니다.
    @AssertTrue(message = "비밀번호는 8~20자이며 영문과 숫자를 포함해야 합니다.")
    public boolean isPasswordPolicyValid() {
        if (password == null || password.isBlank()) {
            return true;
        }
        int length = password.codePointCount(0, password.length());
        return length >= 8 && length <= 20
            && password.chars().anyMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))
            && password.chars().anyMatch(c -> c >= '0' && c <= '9')
            && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
