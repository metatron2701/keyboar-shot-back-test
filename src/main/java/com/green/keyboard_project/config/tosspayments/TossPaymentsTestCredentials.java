package com.green.keyboard_project.config.tosspayments;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** 서버 전용 테스트 시크릿. 공개 getter·Lombok·record로 키를 노출하지 않습니다. */
public final class TossPaymentsTestCredentials {
    @JsonIgnore
    private final String secretKey;

    private TossPaymentsTestCredentials(String secretKey) {
        this.secretKey = secretKey;
    }

    static TossPaymentsTestCredentials fromSecretKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw invalid("TOSS_PAYMENTS_SECRET_KEY가 없습니다. 테스트 설정을 끄거나 개인 실행 환경변수에 상단 주문서형 테스트 시크릿 키를 설정하세요.");
        }
        if (secretKey.startsWith("live_")) {
            throw invalid("운영 키는 결제 테스트 설정에서 사용할 수 없습니다. 상단 주문서형 테스트 시크릿 키를 사용하세요.");
        }
        if (secretKey.startsWith("test_sk_")) {
            throw invalid("API 개별 연동 키는 사용할 수 없습니다. 상단 주문서형·결제창형 연동 키의 테스트 시크릿 키를 사용하세요.");
        }
        if (secretKey.startsWith("test_gck_") || secretKey.startsWith("test_ck_")) {
            throw invalid("클라이언트 키가 아닌 서버용 테스트 시크릿 키를 설정하세요.");
        }
        if (!secretKey.matches("test_gsk_[A-Za-z0-9_-]+")) {
            throw invalid("TOSS_PAYMENTS_SECRET_KEY 형식이 잘못되었습니다. test_gsk_로 시작하는 상단 테스트 시크릿 키 원문을 공백 없이 설정하세요.");
        }
        return new TossPaymentsTestCredentials(secretKey);
    }

    // 향후 같은 패키지의 서버 연동에서만 사용할 수 있습니다. 이번 단계에는 API 클라이언트가 없습니다.
    String secretKey() {
        return secretKey;
    }

    @Override
    public String toString() {
        return "TossPaymentsTestCredentials[secretKey=REDACTED]";
    }

    private static IllegalStateException invalid(String detail) {
        return new IllegalStateException("토스페이먼츠 테스트 설정 오류: " + detail);
    }
}
