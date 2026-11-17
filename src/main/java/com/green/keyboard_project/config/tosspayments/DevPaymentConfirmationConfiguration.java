package com.green.keyboard_project.config.tosspayments;

import com.green.keyboard_project.controller.DevPaymentConfirmationController;
import com.green.keyboard_project.controller.DevPaymentConfirmationExceptionHandler;
import com.green.keyboard_project.dev.tossorders.DevTossOrderService;
import com.green.keyboard_project.service.DevPaymentConfirmationService;
import com.green.keyboard_project.service.payment.InMemoryPaymentConfirmationStore;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration(proxyBeanMethods = false)
@Conditional(DevPaymentConfirmationConfiguration.Enabled.class)
@Import({DevPaymentConfirmationController.class, DevPaymentConfirmationExceptionHandler.class})
public class DevPaymentConfirmationConfiguration {
    public static boolean enabled(Environment env) {
        return env.acceptsProfiles(Profiles.of("local & !prod & !production"))
                && env.getProperty("TOSS_PAYMENTS_DEV_ORDERS_ENABLED", Boolean.class, false)
                && env.getProperty("toss.payments.test.enabled", Boolean.class, false)
                && env.getProperty("toss.payments.dev-confirm.enabled", Boolean.class, false);
    }
    public static class Enabled implements Condition {
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return enabled(context.getEnvironment());
        }
    }
    @Bean InMemoryPaymentConfirmationStore paymentConfirmationStore() {
        return new InMemoryPaymentConfirmationStore(1000);
    }
    @Bean TossPaymentsClient tossPaymentsClient(TossPaymentsTestCredentials credentials, Environment env) {
        return new TossPaymentsClient(credentials, env.getProperty("toss.payments.confirm.read-timeout-seconds", Integer.class, 65));
    }
    @Bean DevPaymentConfirmationService devPaymentConfirmationService(DevTossOrderService orders,
            InMemoryPaymentConfirmationStore store, TossPaymentsClient client) {
        return new DevPaymentConfirmationService(orders, store, client);
    }
}
