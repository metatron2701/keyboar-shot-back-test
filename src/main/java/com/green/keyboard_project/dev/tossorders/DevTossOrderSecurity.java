package com.green.keyboard_project.dev.tossorders;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;

/** 이 클래스는 활성화된 개발 주문 Configuration에서만 import합니다. */
public class DevTossOrderSecurity {
    @Bean @Order(0)
    SecurityFilterChain devTossOrderSecurity(HttpSecurity http) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        return http.securityMatcher(paths.matcher("/api/dev/toss-orders/**"))
            .cors(cors -> cors.configurationSource(request -> {
                var policy = new CorsConfiguration();
                policy.setAllowedOrigins(List.of("http://localhost:3000"));
                policy.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
                policy.setAllowedHeaders(List.of("Accept", "Content-Type"));
                policy.setAllowCredentials(true);
                return policy;
            }))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.ignoringRequestMatchers(paths.matcher(HttpMethod.POST, "/api/dev/toss-orders")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/dev/toss-orders/*")
                    .access((identity, context) -> new AuthorizationDecision(loopback(context.getRequest().getRemoteAddr())))
                .requestMatchers(HttpMethod.POST, "/api/dev/toss-orders")
                    .access((identity, context) -> new AuthorizationDecision(loopback(context.getRequest().getRemoteAddr())))
                .anyRequest().denyAll())
            .build();
    }
    // 이 브랜치에는 회원 공통 체인이 없으므로 초기 프로젝트의 기본 보호를 유지합니다.
    @Bean @Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(name="securityFilterChain")
    SecurityFilterChain devOrdersDefaultProtection(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .formLogin(org.springframework.security.config.Customizer.withDefaults())
            .httpBasic(org.springframework.security.config.Customizer.withDefaults()).build();
    }
    private static boolean loopback(String address) {
        return "127.0.0.1".equals(address) || "::1".equals(address) || "0:0:0:0:0:0:0:1".equals(address);
    }
}
