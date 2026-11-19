package com.green.keyboard_project.config.tosspayments;

import com.green.keyboard_project.dto.payment.PaymentConfirmationRequest;
import com.green.keyboard_project.dto.payment.TossPaymentResponse;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/** 공식 서버로만 통신하며 주소 변경·리다이렉트·응답 원문 출력·자동 재시도·키 공개 접근자를 제공하지 않습니다. */
public class TossPaymentsClient {
    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final Set<String> SAFE_CODES = Set.of("INVALID_API_KEY", "UNAUTHORIZED_KEY",
            "FORBIDDEN_REQUEST", "NOT_FOUND_PAYMENT", "NOT_FOUND_PAYMENT_SESSION",
            "ALREADY_PROCESSED_PAYMENT", "EXPIRED_PAYMENT", "INVALID_REQUEST",
            "INVALID_PAYMENT_KEY", "INVALID_AMOUNT", "INVALID_ORDER_ID", "REJECT_CARD_COMPANY",
            "PAY_PROCESS_CANCELED", "PAY_PROCESS_ABORTED", "PROVIDER_ERROR");
    private final RestClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    public record Failure(int status, String code, boolean uncertain) {}
    public record Reply(TossPaymentResponse payment, Failure failure) {
        // 정상 외부 응답을 내부 서비스가 검증할 수 있는 결과로 묶습니다.
        public static Reply payment(TossPaymentResponse value) { return new Reply(value, null); }
        // 실패 상태와 미확정 여부만 전달하고 외부 오류 본문은 보관하지 않습니다.
        public static Reply failure(int status, String code, boolean uncertain) {
            return new Reply(null, new Failure(status, code, uncertain));
        }
        // 진단 출력에 결제 키나 외부 응답 원문이 나타나지 않게 합니다.
        @Override public String toString() { return "TossPaymentsReply[redacted]"; }
    }

    // 테스트 인증 정보와 제한 시간을 받아 통신 준비만 수행합니다. 생성 자체로 토스 요청을 보내지는 않습니다.
    public TossPaymentsClient(TossPaymentsTestCredentials credentials, int readTimeoutSeconds) {
        this(credentials, builder(readTimeoutSeconds));
    }

    // 같은 패키지의 테스트에서는 모의 응답 서버를 연결하므로 실제 토스에 접속하지 않습니다.
    // 실제 연결과 모의 통신이 같은 처리 흐름을 사용하도록 구성 지점을 분리합니다.
    TossPaymentsClient(TossPaymentsTestCredentials credentials, RestClient.Builder builder) {
        this.http = builder.baseUrl(BASE_URL)
                .defaultHeaders(headers -> headers.setBasicAuth(credentials.secretKey(), "", StandardCharsets.UTF_8))
                .build();
    }

    // 연결·읽기 시간에 상한을 두고 리다이렉트를 금지해 승인 요청이 예상하지 못한 서버로 전달되지 않게 합니다.
    private static RestClient.Builder builder(int seconds) {
        if (seconds < 60 || seconds > 120) throw new IllegalArgumentException("Confirmation timeout must be 60..120 seconds");
        HttpClient transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofSeconds(seconds));
        return RestClient.builder().requestFactory(factory);
    }

    // 프론트 금액을 그대로 보내지 않고 서버가 확인한 금액으로 공식 승인을 요청합니다. 같은 주문에는 같은 멱등키를 사용합니다.
    public Reply confirm(PaymentConfirmationRequest request, long serverAmount, String idempotencyKey) {
        try {
            var response = http.post().uri("/v1/payments/confirm")
                    .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", idempotencyKey)
                    .body(Map.of("paymentKey", request.paymentKey(), "orderId", request.orderId(), "amount", serverAmount))
                    .retrieve().toEntity(String.class);
            return response.getStatusCode().value() == 200 ? parsePayment(response.getBody()) : invalid();
        } catch (RestClientResponseException error) {
            return parseFailure(error);
        // 통신 오류만으로 결제사의 승인 여부를 알 수 없으므로 확정 실패가 아닌 미확정으로 반환합니다.
        } catch (RestClientException error) {
            return Reply.failure(0, "TRANSPORT_ERROR", true);
        }
    }

    // 승인 응답을 못 받은 경우 결제 키로 결과만 조회합니다. 불확실한 결제를 다시 승인하지 않기 위한 경로입니다.
    public Reply find(String paymentKey) {
        try {
            var response = http.get().uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve().toEntity(String.class);
            return response.getStatusCode().value() == 200 ? parsePayment(response.getBody()) : invalid();
        } catch (RestClientResponseException error) {
            return parseFailure(error);
        // 통신 오류만으로 결제사의 승인 여부를 알 수 없으므로 확정 실패가 아닌 미확정으로 반환합니다.
        } catch (RestClientException error) {
            return Reply.failure(0, "TRANSPORT_ERROR", true);
        }
    }

    // 금액의 정수 형식과 승인 시각을 읽어 내부 응답으로 바꿉니다. 주문·금액 일치 여부는 서비스에서 다시 검증합니다.
    private Reply parsePayment(String body) {
        try {
            JsonNode n = json.readTree(body);
            if (n == null || !n.isObject() || !n.path("totalAmount").isIntegralNumber()
                    || !n.path("totalAmount").canConvertToLong()) return invalid();
            String approved = text(n, "approvedAt");
            return Reply.payment(new TossPaymentResponse(text(n, "paymentKey"), text(n, "orderId"),
                    n.path("totalAmount").longValue(), text(n, "currency"), text(n, "status"),
                    text(n, "method"), approved == null ? null : OffsetDateTime.parse(approved)));
        } catch (RuntimeException error) { return invalid(); }
    }

    // 성공처럼 보이는 응답도 해석할 수 없으면 미확정으로 돌려 완료 처리를 막습니다.
    private static Reply invalid() { return Reply.failure(502, "INVALID_PROVIDER_RESPONSE", true); }
    // 문자열 필드만 읽고 다른 자료형을 문자열로 임의 변환하지 않습니다.
    private static String text(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText() : null;
    }

    // 허용한 외부 오류 코드만 남깁니다. 서버 오류·요청 충돌·시간 초과 등은 승인 결과를 확정할 수 없는 상태로 구분합니다.
    private Reply parseFailure(RestClientResponseException error) {
        int status = error.getStatusCode().value();
        String code = "PROVIDER_ERROR";
        try {
            String candidate = text(json.readTree(error.getResponseBodyAsString()), "code");
            if (SAFE_CODES.contains(candidate)) code = candidate;
        } catch (RuntimeException ignored) { /* 외부 응답 원문은 보관하지 않습니다. */ }
        boolean uncertain = status >= 500 || status == 408 || status == 409 || status == 429
                || "ALREADY_PROCESSED_PAYMENT".equals(code);
        return Reply.failure(status, code, uncertain);
    }
}
