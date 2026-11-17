package com.green.keyboard_project;

import com.green.keyboard_project.config.tosspayments.*;
import com.green.keyboard_project.controller.DevPaymentConfirmationController;
import com.green.keyboard_project.dev.tossorders.DevTossOrdersConfiguration;
import com.green.keyboard_project.security.DevPaymentConfirmationSecurity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.cors.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DevPaymentConfirmationIsolationTests {
    @Configuration @EnableWebSecurity
    static class PermissiveFallback {
        @Bean @Order(100) SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }
        @Bean CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration policy = new CorsConfiguration();
            policy.setAllowedOrigins(List.of("http://localhost:3000"));
            policy.setAllowedMethods(List.of("POST", "OPTIONS"));
            policy.setAllowedHeaders(List.of("Content-Type"));
            policy.setAllowCredentials(true);
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", policy);
            return source;
        }
    }
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class))
            .withUserConfiguration(PermissiveFallback.class, DevPaymentConfirmationSecurity.class,
                    DevPaymentConfirmationConfiguration.class, TossPaymentsTestConfiguration.class, DevTossOrdersConfiguration.class)
            .withPropertyValues("spring.profiles.active=local", "TOSS_PAYMENTS_DEV_ORDERS_ENABLED=true",
                    "toss.payments.test.enabled=true", "toss.payments.dev-confirm.enabled=true",
                    "TOSS_PAYMENTS_SECRET_KEY=test_gsk_fixture_only", "cors.allowed-origin=http://localhost:3000");

    @ParameterizedTest @ValueSource(strings = {"default", "prod", "production", "local,prod", "local,production"})
    void nonLocalAndProductionProfilesNeverRegisterApproval(String profiles) {
        runner.withPropertyValues("spring.profiles.active="+profiles).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DevPaymentConfirmationController.class);
            assertThat(context).doesNotHaveBean(TossPaymentsClient.class);
            var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
            mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.data.code").value("PAYMENT_CONFIRM_FORBIDDEN"));
        });
    }

    @ParameterizedTest @ValueSource(strings = {"TOSS_PAYMENTS_DEV_ORDERS_ENABLED", "toss.payments.test.enabled", "toss.payments.dev-confirm.enabled"})
    void everyOptInIsRequiredEvenWithPermissiveFallback(String flag) {
        runner.withPropertyValues(flag+"=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DevPaymentConfirmationController.class);
            assertThat(context).doesNotHaveBean(TossPaymentsClient.class);
            var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
            mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        });
    }
}
