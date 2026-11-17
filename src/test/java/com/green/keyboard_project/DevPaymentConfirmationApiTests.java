package com.green.keyboard_project;

import com.green.keyboard_project.config.tosspayments.TossPaymentsClient;
import com.green.keyboard_project.dev.tossorders.DevTossOrderService;
import com.green.keyboard_project.dto.payment.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.profiles.active=local", "TOSS_PAYMENTS_DEV_ORDERS_ENABLED=true",
        "toss.payments.test.enabled=true", "toss.payments.dev-confirm.enabled=true",
        "TOSS_PAYMENTS_SECRET_KEY=test_gsk_fixture_only"})
@ExtendWith(OutputCaptureExtension.class)
class DevPaymentConfirmationApiTests {
    @Autowired WebApplicationContext context;
    @Autowired DevTossOrderService orders;
    @MockitoBean TossPaymentsClient client;
    MockMvc mvc;
    String order;
    String key;

    @BeforeEach void setup() {
        reset(client);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        order = orders.create().orderId();
        key = "fixture_" + order;
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.payment(
                new TossPaymentResponse(key, order, 10000, "KRW", "DONE", "카드",
                        OffsetDateTime.parse("2026-11-16T10:00:00+09:00"))));
    }

    @Test void confirmationUsesExistingApiEnvelopeAndNeverExposesKey() throws Exception {
        var response = mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                        .contentType("application/json").content(body("10000")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.paymentStatus").value("DONE"))
                .andExpect(jsonPath("$.data.storage").value("MEMORY_ONLY"))
                .andExpect(jsonPath("$.data.paymentKey").doesNotExist()).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain(key, "test_gsk_");
        mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                .contentType("application/json").content(body("10000"))).andExpect(status().isOk());
        verify(client, times(1)).confirm(any(), eq(10000L), anyString());
    }

    @ParameterizedTest @ValueSource(strings = {"\"10000\"", "10000.0", "10000.5", "0", "-1", "null", "9223372036854775808"})
    void invalidAmountsAreRejectedWithoutProviderCalls(String value) throws Exception {
        mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                .contentType("application/json").content(body(value)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.code").value("INVALID_CONFIRM_REQUEST"));
        verifyNoInteractions(client);
    }

    @Test void malformedAndMissingAndInvalidKeyBodiesAreSafe() throws Exception {
        for (String value : new String[]{"{", "{}", body("10000").replace(key, "bad key"), body("10000").replace(key, "x".repeat(201)),
                body("10000").replace("\""+key+"\"", "123")}) {
            var response = mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                    .contentType("application/json").content(value)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false)).andReturn().getResponse();
            assertThat(response.getContentAsString()).doesNotContain(key, "bad key");
        }
        verifyNoInteractions(client);
    }

    @Test void serverAmountAndMissingOrderAreCheckedBeforeCallingProvider() throws Exception {
        mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                .contentType("application/json").content(body("1")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.code").value("PAYMENT_AMOUNT_MISMATCH"));
        mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                .contentType("application/json").content(body("10000").replace(order, "dev_"+"f".repeat(32))))
                .andExpect(status().isNotFound());
        verifyNoInteractions(client);
    }

    @Test void unknownReturns202AndRetryQueriesWithoutAnotherApproval() throws Exception {
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.failure(0, "TRANSPORT_ERROR", true));
        when(client.find(key)).thenReturn(TossPaymentsClient.Reply.failure(404, "NOT_FOUND_PAYMENT", false));
        for (int i=0; i<2; i++) {
            mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                    .contentType("application/json").content(body("10000")))
                    .andExpect(status().isAccepted()).andExpect(header().string("Retry-After", "3"))
                    .andExpect(jsonPath("$.data.paymentStatus").value("UNKNOWN"))
                    .andExpect(jsonPath("$.data.completed").value(false));
        }
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
        verify(client, times(1)).find(key);
    }

    @Test void providerConfigurationErrorsAreSanitizedAndDoNotModifyMemberRoutes() throws Exception {
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.failure(401, "INVALID_API_KEY", false));
        mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                .contentType("application/json").content(body("10000")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.data.code").value("PAYMENT_PROVIDER_CONFIGURATION_ERROR"))
                .andExpect(jsonPath("$.data.providerCode").value("INVALID_API_KEY"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/products").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()); // CSRF rejection must remain enabled in the fallback chain.
        mvc.perform(post("/admin/products").contentType("application/json").content("{}")
                .with(request -> {
                    var repository = new HttpSessionCsrfTokenRepository();
                    var token = repository.generateToken(request);
                    repository.saveToken(token, request, new MockHttpServletResponse());
                    new XorCsrfTokenRequestAttributeHandler().handle(request, new MockHttpServletResponse(), () -> token);
                    var submitted = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                    request.addHeader(submitted.getHeaderName(), submitted.getToken());
                    return request;
                }))
                .andExpect(status().isUnauthorized());
    }

    @Test void untrustedMissingOriginNonLoopbackAndNonJsonCannotApprove() throws Exception {
        mvc.perform(post("/api/payments/confirm").contentType("application/json").content(body("10000")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/confirm").header("Origin", "https://untrusted.example")
                .contentType("application/json").content(body("10000"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                .with(request -> {request.setRemoteAddr("192.0.2.1"); return request;})
                .header("X-Forwarded-For", "127.0.0.1").contentType("application/json").content(body("10000")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                .contentType("text/plain").content(body("10000"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/payments/confirm")).andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }

    @Test void trustedCorsPreflightUsesExistingCredentialPolicy() throws Exception {
        mvc.perform(options("/api/payments/confirm").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/payments/confirm").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/payments/confirm").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }

    @Test void invalidInputDoesNotLeakPaymentKeyIntoApplicationLogs(CapturedOutput output) throws Exception {
        String marker="fixture_sensitive_key_no_log";
        var response = mvc.perform(post("/api/payments/confirm").header("Origin", "http://localhost:3000")
                .contentType("application/json").content(body("0").replace(key, marker)))
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain(marker);
        assertThat(output.getAll()).doesNotContain(marker);
        assertThat(new PaymentConfirmationRequest(marker, order, 1L).toString()).doesNotContain(marker);
        verifyNoInteractions(client);
    }

    @Test void waitingForDepositIsNotCompletedAndRepeatedRequestNeverReapproves() throws Exception {
        when(client.confirm(any(),anyLong(),anyString())).thenReturn(TossPaymentsClient.Reply.payment(
                new TossPaymentResponse(key,order,10000,"KRW","WAITING_FOR_DEPOSIT","가상계좌",null)));
        for(int i=0;i<2;i++) {
            mvc.perform(post("/api/payments/confirm").header("Origin","http://localhost:3000")
                    .contentType("application/json").content(body("10000")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("WAITING_FOR_DEPOSIT"))
                    .andExpect(jsonPath("$.data.completed").value(false)).andExpect(jsonPath("$.data.storage").value("MEMORY_ONLY"));
        }
        verify(client,times(1)).confirm(any(),anyLong(),anyString()); verify(client,never()).find(anyString());
    }

    @Test void contradictoryWaitingResultUsesExistingUnknownContract() throws Exception {
        when(client.confirm(any(),anyLong(),anyString())).thenReturn(TossPaymentsClient.Reply.payment(
                new TossPaymentResponse(key,order,10000,"KRW","WAITING_FOR_DEPOSIT","가상계좌",OffsetDateTime.now())));
        mvc.perform(post("/api/payments/confirm").header("Origin","http://localhost:3000")
                .contentType("application/json").content(body("10000")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.paymentStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.completed").value(false)).andExpect(header().string("Retry-After","3"));
    }

    private String body(String amount) {
        return "{\"paymentKey\":\""+key+"\",\"orderId\":\""+order+"\",\"amount\":"+amount+"}";
    }
}
