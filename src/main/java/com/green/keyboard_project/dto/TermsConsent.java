package com.green.keyboard_project.dto;

import static com.green.keyboard_project.dto.SignupRequest.TERMS_REQUIRED_MESSAGE;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;

/** 회원가입 요청의 필수 약관 동의 여부를 검증합니다. */
public record TermsConsent(JsonNode serviceTerms, JsonNode privacyPolicy) {
    @AssertTrue(message = TERMS_REQUIRED_MESSAGE)
    public boolean isRequiredConsentGranted() {
        // 문자열이나 숫자를 동의 값으로 변환하지 않고 JSON의 true만 인정합니다.
        return isAgreed(serviceTerms) && isAgreed(privacyPolicy);
    }

    private static boolean isAgreed(JsonNode consent) {
        return consent != null && consent.isBoolean() && consent.booleanValue();
    }
}
