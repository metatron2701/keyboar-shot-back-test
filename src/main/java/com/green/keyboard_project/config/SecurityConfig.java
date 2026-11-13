package com.green.keyboard_project.config;

import com.green.keyboard_project.security.JwtAuthenticationFilter;
import com.green.keyboard_project.security.JwtTokenProvider;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@AllArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider; // 토큰 인증 프로바이더 주입

    // 비밀번호 암호화에 사용할 BCrypt 해시 인코더 빈 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 스프링 시큐리티 인증 관리자 빈 등록
    @Bean
    AuthenticationManager authenticationManager(
        AuthenticationConfiguration configuration
    ) {
        return configuration.getAuthenticationManager();
    }

    // HTTP 보안 필터 체인 설정
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. CORS(교차 출처 리소스 공유) 설정 적용
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 2. REST API 특성상 불필요한 CSRF(사이트 간 요청 위조) 보안 비활성화
            .csrf(AbstractHttpConfigurer::disable)

            // 3. 엔드포인트별 인가 규칙 정의
            .authorizeHttpRequests(auth -> auth
                    // 회원 관련 API (회원가입, 로그인 등) 허용
                    .requestMatchers("/api/users", "/api/users/**").permitAll()

                    // 주문 관련 API 허용 (포스트맨 테스트 및 비회원 주문)
                    .requestMatchers("/api/orders", "/api/orders/**").permitAll()

                    // 상품 조회 관련 API 허용 (GET 요청)
                    .requestMatchers(HttpMethod.GET,
                            "/api/product", "/api/product/**",
                            "/api/products", "/api/products/**"
                    ).permitAll()

                    // 장바구니 관련 API 허용 (토큰 유무는 컨트롤러/필터에서 검증)
                    .requestMatchers("/api/cart", "/api/cart/**").permitAll()

                    // Swagger UI 및 API 명세 문서 경로 허용
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                    // 그 외 모든 요청은 인증 필요
                    .anyRequest().authenticated()
            )
            // JWT 인증 필터 등록 (요청 쿠키에서 access_token을 읽어 사용자 인증 정보 주입)
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // CORS 세부 정책 설정 (프론트엔드 localhost:3000 연동용)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 허용할 프론트엔드 출처(Origin) 목록
        config.setAllowedOrigins(List.of(
            "http://localhost:3000",
            "http://127.0.0.1:3000"
        ));

        // 허용할 HTTP 메서드
        config.setAllowedMethods(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // 모든 요청 헤더 허용
        config.setAllowedHeaders(List.of("*"));

        // 쿠키 및 인증 헤더 포함 허용
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
