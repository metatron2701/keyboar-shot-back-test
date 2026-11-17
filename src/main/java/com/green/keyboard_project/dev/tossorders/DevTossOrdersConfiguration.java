package com.green.keyboard_project.dev.tossorders;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Import;

/** local 명시 + 별도 opt-in 필요. prod/production 동시 활성화도 허용하지 않습니다. */
@Configuration(proxyBeanMethods = false)
@Profile("local & !prod & !production")
@ConditionalOnProperty(name = "TOSS_PAYMENTS_DEV_ORDERS_ENABLED", havingValue = "true")
@Import({DevTossOrderController.class, DevTossOrderErrors.class, DevTossOrderSecurity.class})
public class DevTossOrdersConfiguration {
    @Bean DevTossOrderService devTossOrderService() { return new DevTossOrderService(); }
}
