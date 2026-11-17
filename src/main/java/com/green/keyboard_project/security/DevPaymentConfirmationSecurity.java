package com.green.keyboard_project.security;

import com.green.keyboard_project.config.tosspayments.DevPaymentConfirmationConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import java.util.List;

/** Always installed: the generic permitAll rule must never expose a disabled approval route. */
@Configuration(proxyBeanMethods = false)
public class DevPaymentConfirmationSecurity {
    @Bean @Order(-1)
    SecurityFilterChain paymentConfirmationFilterChain(HttpSecurity http, Environment env) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        return http.securityMatcher("/api/payments/confirm", "/api/payments/confirm/**")
                .cors(cors -> cors.configurationSource(request -> {
                    var policy = new CorsConfiguration();
                    String trusted = env.getProperty("cors.allowed-origin");
                    if (trusted != null && !trusted.isBlank()) policy.setAllowedOrigins(List.of(trusted));
                    policy.setAllowedMethods(List.of("POST", "OPTIONS"));
                    policy.setAllowedHeaders(List.of("Accept", "Content-Type"));
                    policy.setAllowCredentials(true);
                    return policy;
                }))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.ignoringRequestMatchers(paths.matcher(HttpMethod.POST, "/api/payments/confirm")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/payments/confirm")
                        .access((identity, context) -> {
                            var request = context.getRequest();
                            String remote = request.getRemoteAddr();
                            boolean local = "127.0.0.1".equals(remote) || "::1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote);
                            String trusted = env.getProperty("cors.allowed-origin");
                            return new AuthorizationDecision(DevPaymentConfirmationConfiguration.enabled(env) && local
                                    && trusted != null && trusted.equals(request.getHeader("Origin"))
                                    && json(request.getContentType()));
                        }).anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> forbidden(response))
                        .accessDeniedHandler((request, response, error) -> forbidden(response)))
                .build();
    }
    private static boolean json(String contentType) {
        if (contentType == null) return false;
        try {
            var type = org.springframework.http.MediaType.parseMediaType(contentType);
            return "application".equals(type.getType()) && "json".equals(type.getSubtype());
        } catch (IllegalArgumentException invalid) { return false; }
    }
    private static void forbidden(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setStatus(403);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"success\":false,\"message\":\"Development confirmation is unavailable.\",\"data\":{\"code\":\"PAYMENT_CONFIRM_FORBIDDEN\"}}");
    }
}
