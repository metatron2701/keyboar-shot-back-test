package com.green.keyboard_project.config.tosspayments;

import com.green.keyboard_project.dto.payment.PaymentConfirmationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossPaymentsClientTests {
    private static final String ORDER = "dev_" + "a".repeat(32);
    private static final String KEY = "fixture_payment_only";
    private static final String SECRET = "test_gsk_fixture_only";
    private MockRestServiceServer server;
    private TossPaymentsClient client;
    private final PaymentConfirmationRequest request = new PaymentConfirmationRequest(KEY, ORDER, 10000L);

    @BeforeEach void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossPaymentsClient(TossPaymentsTestCredentials.fromSecretKey(SECRET), builder);
    }

    @Test void fixedHttpsEndpointBasicAuthenticationBodyAndIdempotencyHeader() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder().encodeToString((SECRET + ":").getBytes(StandardCharsets.UTF_8))))
                .andExpect(header("Idempotency-Key", "fixture_idempotency"))
                .andExpect(content().json("{\"paymentKey\":\""+KEY+"\",\"orderId\":\""+ORDER+"\",\"amount\":10000}"))
                .andRespond(withSuccess(payment(), MediaType.APPLICATION_JSON));
        var reply = client.confirm(request, 10000, "fixture_idempotency");
        assertThat(reply.failure()).isNull();
        assertThat(reply.payment().status()).isEqualTo("DONE");
        assertThat(reply.payment().approvedAt()).isNotNull();
        assertThat(reply.toString()).doesNotContain(KEY, SECRET);
        assertThat(reply.payment().toString()).doesNotContain(KEY);
        server.verify();
    }

    @Test void recoveryUsesPaymentKeyGetWithoutIdempotencyHeaderOrOrderLookup() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/"+KEY))
                .andExpect(method(HttpMethod.GET)).andExpect(headerDoesNotExist("Idempotency-Key"))
                .andRespond(withSuccess(payment(), MediaType.APPLICATION_JSON));
        assertThat(client.find(KEY).payment().orderId()).isEqualTo(ORDER);
        server.verify();
    }

    @Test void opaquePaymentKeyIsEncodedAsOnePathValueNotAQueryOrFragment() {
        server.expect(request -> {
            assertThat(request.getURI().getHost()).isEqualTo("api.tosspayments.com");
            assertThat(request.getURI().getRawPath()).isEqualTo("/v1/payments/a%2Fb%3Fc%23d%25e%2Bf");
            assertThat(request.getURI().getRawQuery()).isNull();
            assertThat(request.getURI().getRawFragment()).isNull();
        }).andRespond(withSuccess(payment(), MediaType.APPLICATION_JSON));
        client.find("a/b?c#d%e+f");
        server.verify();
    }

    @Test void unauthorizedResponseRetainsOnlyAllowlistedCode() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"INVALID_API_KEY\",\"message\":\""+SECRET+" " +KEY+"\"}"));
        var reply = client.confirm(request, 10000, "fixture");
        assertThat(reply.failure().status()).isEqualTo(401);
        assertThat(reply.failure().code()).isEqualTo("INVALID_API_KEY");
        assertThat(reply.failure().uncertain()).isFalse();
        assertThat(reply.failure().toString()).doesNotContain(SECRET, KEY);
        server.verify();
    }

    @ParameterizedTest @ValueSource(ints = {408, 409, 429, 500, 503})
    void uncertainHttpFailuresNeverBecomeDefiniteRejections(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status)).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"UNTRUSTED_TOKEN_VALUE\",\"message\":\"do-not-expose\"}"));
        var failure = client.confirm(request, 10000, "fixture").failure();
        assertThat(failure.uncertain()).isTrue();
        assertThat(failure.code()).isEqualTo("PROVIDER_ERROR");
        server.verify();
    }

    @Test void transportFailureIsUncertainAndNotRetried() {
        server.expect(anything()).andRespond(request -> { throw new IOException("fixture private diagnostic"); });
        var reply = client.confirm(request, 10000, "fixture");
        assertThat(reply.failure().uncertain()).isTrue();
        assertThat(reply.failure().toString()).doesNotContain("private diagnostic");
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"not-json", "[]", "{}", "{\"totalAmount\":10000.5}", "{\"totalAmount\":\"10000\"}"})
    void malformedSuccessfulResponsesAreUncertain(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThat(client.confirm(request, 10000, "fixture").failure().uncertain()).isTrue();
        server.verify();
    }

    @Test void unexpectedHttpSuccessStatusIsNotAnApproval() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(payment()));
        assertThat(client.confirm(request, 10000, "fixture").failure().uncertain()).isTrue();
        server.verify();
    }

    @Test void timeoutBoundsAreValidatedWithoutOpeningAConnection() {
        var credentials = TossPaymentsTestCredentials.fromSecretKey(SECRET);
        assertThatThrownBy(() -> new TossPaymentsClient(credentials, 59)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TossPaymentsClient(credentials, 121)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new TossPaymentsClient(credentials, 65)).doesNotThrowAnyException();
    }

    @Test void socketTimeoutIsUncertainWithoutLeakingDiagnosticsOrRetrying() {
        server.expect(anything()).andRespond(request -> {throw new java.net.SocketTimeoutException("private timeout diagnostic");});
        var result=client.confirm(request,10000,"fixture");
        assertThat(result.failure().uncertain()).isTrue();
        assertThat(result.failure().toString()).doesNotContain("private timeout diagnostic");
        server.verify();
    }

    private String payment() {
        return "{\"paymentKey\":\""+KEY+"\",\"orderId\":\""+ORDER+"\",\"totalAmount\":10000,"
                + "\"currency\":\"KRW\",\"status\":\"DONE\",\"method\":\"카드\",\"approvedAt\":\"2026-11-16T10:00:00+09:00\"}";
    }
}
