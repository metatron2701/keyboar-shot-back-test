package com.green.keyboard_project.config.tosspayments;

import com.green.keyboard_project.controller.DevPaymentConfirmationController;
import com.green.keyboard_project.controller.DevPaymentConfirmationExceptionHandler;
import com.green.keyboard_project.dev.tossorders.DevTossOrderService;
import com.green.keyboard_project.service.payment.DevPaymentConfirmationService;
import com.green.keyboard_project.service.payment.InMemoryPaymentConfirmationStore;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration(proxyBeanMethods = false)
@Conditional(DevPaymentConfirmationConfiguration.Enabled.class)
@Import({DevPaymentConfirmationController.class, DevPaymentConfirmationExceptionHandler.class})
public class DevPaymentConfirmationConfiguration {
    // 로컬 프로필과 개발 주문·테스트 키·승인 활성화 설정을 모두 요구합니다. 운영 프로필이 함께 켜져 있으면 등록하지 않습니다.
    public static boolean enabled(Environment env) {
        return env.acceptsProfiles(Profiles.of("local & !prod & !production"))
                && env.getProperty("TOSS_PAYMENTS_DEV_ORDERS_ENABLED", Boolean.class, false)
                && env.getProperty("toss.payments.test.enabled", Boolean.class, false)
                && env.getProperty("toss.payments.dev-confirm.enabled", Boolean.class, false);
    }
    public static class Enabled implements Condition {
        // 같은 활성화 검사를 컨트롤러와 설정 등록에 재사용하여 조건이 서로 어긋나지 않게 합니다.
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return enabled(context.getEnvironment());
        }
    }
    // 한 서버 프로세스 안에서 승인 중복과 미확정 상태를 관리할 제한된 크기의 메모리 저장소를 만듭니다.
    @Bean InMemoryPaymentConfirmationStore paymentConfirmationStore() {
        return new InMemoryPaymentConfirmationStore(1000);
    }
    // 검증된 테스트 시크릿 키와 읽기 제한 시간으로 공식 토스 통신 클라이언트를 준비합니다.
    @Bean TossPaymentsClient tossPaymentsClient(TossPaymentsTestCredentials credentials, Environment env) {
        return new TossPaymentsClient(credentials, env.getProperty("toss.payments.confirm.read-timeout-seconds", Integer.class, 65));
    }
    // 개발 주문 조회·승인 상태 저장·외부 통신을 조립합니다. 실제 주문 DB 저장 서비스는 여기에서 연결하지 않습니다.
    @Bean DevPaymentConfirmationService devPaymentConfirmationService(DevTossOrderService orders,
            InMemoryPaymentConfirmationStore store, TossPaymentsClient client) {
        return new DevPaymentConfirmationService(orders, store, client);
    }
}
