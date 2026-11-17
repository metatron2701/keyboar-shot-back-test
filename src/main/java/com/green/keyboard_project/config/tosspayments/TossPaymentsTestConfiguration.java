package com.green.keyboard_project.config.tosspayments;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** 주문서형 결제의 테스트 설정 준비만 담당하며 API 호출이나 보안 정책을 추가하지 않습니다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "toss.payments.test", name = "enabled", havingValue = "true")
public class TossPaymentsTestConfiguration {

    @Bean
    TossPaymentsTestCredentials tossPaymentsTestCredentials(Environment environment) {
        final String secretKey;
        try {
            secretKey = environment.getProperty("TOSS_PAYMENTS_SECRET_KEY");
        } catch (IllegalArgumentException exception) {
            // 값 또는 원본 예외를 출력하지 않아 잘못된 설정에서도 키를 노출하지 않습니다.
            throw new IllegalStateException("토스페이먼츠 테스트 설정 오류: TOSS_PAYMENTS_SECRET_KEY의 환경변수 참조를 확인하세요.");
        }
        return TossPaymentsTestCredentials.fromSecretKey(secretKey);
    }
}
