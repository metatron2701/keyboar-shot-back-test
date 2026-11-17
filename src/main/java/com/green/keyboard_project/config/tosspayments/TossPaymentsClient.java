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

/** No configurable host, redirects, raw-body logging, implicit retry or public key accessor. */
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
        public static Reply payment(TossPaymentResponse value) { return new Reply(value, null); }
        public static Reply failure(int status, String code, boolean uncertain) {
            return new Reply(null, new Failure(status, code, uncertain));
        }
        @Override public String toString() { return "TossPaymentsReply[redacted]"; }
    }

    public TossPaymentsClient(TossPaymentsTestCredentials credentials, int readTimeoutSeconds) {
        this(credentials, builder(readTimeoutSeconds));
    }

    // Package-private test seam: tests bind MockRestServiceServer, never a real socket.
    TossPaymentsClient(TossPaymentsTestCredentials credentials, RestClient.Builder builder) {
        this.http = builder.baseUrl(BASE_URL)
                .defaultHeaders(headers -> headers.setBasicAuth(credentials.secretKey(), "", StandardCharsets.UTF_8))
                .build();
    }

    private static RestClient.Builder builder(int seconds) {
        if (seconds < 60 || seconds > 120) throw new IllegalArgumentException("Confirmation timeout must be 60..120 seconds");
        HttpClient transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofSeconds(seconds));
        return RestClient.builder().requestFactory(factory);
    }

    public Reply confirm(PaymentConfirmationRequest request, long serverAmount, String idempotencyKey) {
        try {
            var response = http.post().uri("/v1/payments/confirm")
                    .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", idempotencyKey)
                    .body(Map.of("paymentKey", request.paymentKey(), "orderId", request.orderId(), "amount", serverAmount))
                    .retrieve().toEntity(String.class);
            return response.getStatusCode().value() == 200 ? parsePayment(response.getBody()) : invalid();
        } catch (RestClientResponseException error) {
            return parseFailure(error);
        } catch (RestClientException error) {
            return Reply.failure(0, "TRANSPORT_ERROR", true);
        }
    }

    public Reply find(String paymentKey) {
        try {
            var response = http.get().uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve().toEntity(String.class);
            return response.getStatusCode().value() == 200 ? parsePayment(response.getBody()) : invalid();
        } catch (RestClientResponseException error) {
            return parseFailure(error);
        } catch (RestClientException error) {
            return Reply.failure(0, "TRANSPORT_ERROR", true);
        }
    }

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

    private static Reply invalid() { return Reply.failure(502, "INVALID_PROVIDER_RESPONSE", true); }
    private static String text(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText() : null;
    }

    private Reply parseFailure(RestClientResponseException error) {
        int status = error.getStatusCode().value();
        String code = "PROVIDER_ERROR";
        try {
            String candidate = text(json.readTree(error.getResponseBodyAsString()), "code");
            if (SAFE_CODES.contains(candidate)) code = candidate;
        } catch (RuntimeException ignored) { /* No provider text is retained. */ }
        boolean uncertain = status >= 500 || status == 408 || status == 409 || status == 429
                || "ALREADY_PROCESSED_PAYMENT".equals(code);
        return Reply.failure(status, code, uncertain);
    }
}
