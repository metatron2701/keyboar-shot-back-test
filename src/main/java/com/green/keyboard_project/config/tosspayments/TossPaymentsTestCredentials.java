package com.green.keyboard_project.config.tosspayments;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** 서버 전용 테스트 시크릿. 공개 getter·Lombok·record로 키를 노출하지 않습니다. */
public final class TossPaymentsTestCredentials {
    @JsonIgnore
    private final String secretKey;

    // 검사를 통과한 시크릿 키만 보관하며 외부에서 임의로 객체를 만들지 못하게 합니다.
    private TossPaymentsTestCredentials(String secretKey) {
        this.secretKey = secretKey;
    }

    // 운영 키·클라이언트 키·다른 연동 종류의 키를 거부합니다. 키 형식 검사는 같은 상점의 키 쌍인지 확인하는 절차와 별개입니다.
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

    // 같은 패키지의 토스 API 클라이언트가 사용하며 공개 접근자는 제공하지 않습니다.
    // 같은 패키지의 토스 API 클라이언트만 인증 헤더 생성에 사용합니다. 공개 응답 데이터에는 포함하지 않습니다.
    String secretKey() {
        return secretKey;
    }

    // 객체를 실수로 출력해도 시크릿 키 원문이 나타나지 않도록 마스킹된 설명만 반환합니다.
    @Override
    public String toString() {
        return "TossPaymentsTestCredentials[secretKey=REDACTED]";
    }

    // 설정 오류를 일정한 형식으로 만들되 실제 입력한 키 값은 메시지에 넣지 않습니다.
    private static IllegalStateException invalid(String detail) {
        return new IllegalStateException("토스페이먼츠 테스트 설정 오류: " + detail);
    }
}
