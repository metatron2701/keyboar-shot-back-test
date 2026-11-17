package com.green.keyboard_project;

import com.green.keyboard_project.config.tosspayments.TossPaymentsClient;
import com.green.keyboard_project.dev.tossorders.*;
import com.green.keyboard_project.dto.payment.*;
import com.green.keyboard_project.exceptions.PaymentConfirmationException;
import com.green.keyboard_project.service.DevPaymentConfirmationService;
import com.green.keyboard_project.service.payment.InMemoryPaymentConfirmationStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DevPaymentConfirmationServiceTests {
    private DevTossOrderService orders;
    private TossPaymentsClient client;
    private DevPaymentConfirmationService service;
    private PaymentConfirmationRequest request;

    @BeforeEach void setup() {
        orders = new DevTossOrderService();
        client = mock(TossPaymentsClient.class);
        service = new DevPaymentConfirmationService(orders, new InMemoryPaymentConfirmationStore(1000), client);
        request = new PaymentConfirmationRequest("fixture_payment_only", orders.create().orderId(), 10000L);
    }

    @Test void successfulApprovalIsValidatedAndReplayedWithoutAnotherPost() {
        when(client.confirm(eq(request), eq(10000L), anyString())).thenReturn(TossPaymentsClient.Reply.payment(done()));
        var first = service.confirm(request);
        assertThat(first.completed()).isTrue();
        assertThat(first.storage()).isEqualTo("MEMORY_ONLY");
        assertThat(first.toString()).doesNotContain(request.paymentKey());
        assertThat(service.confirm(request)).isEqualTo(first);
        verify(client, times(1)).confirm(eq(request), eq(10000L), matches("dev-confirm-[a-f0-9]{64}"));
        verifyNoMoreInteractions(client);
    }

    @Test void amountMismatchAndUnknownOrderNeverReachProvider() {
        assertCode(() -> service.confirm(new PaymentConfirmationRequest(request.paymentKey(), request.orderId(), 1L)), "PAYMENT_AMOUNT_MISMATCH");
        assertCode(() -> service.confirm(new PaymentConfirmationRequest(request.paymentKey(), "dev_"+"f".repeat(32), 10000L)), "DEV_ORDER_NOT_FOUND");
        verifyNoInteractions(client);
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "fixture key", "fixture\nkey", "한글"})
    void unsafeKeyNeverReachesProvider(String key) {
        assertCode(() -> service.confirm(new PaymentConfirmationRequest(key, request.orderId(), 10000L)), "INVALID_CONFIRM_REQUEST");
        verifyNoInteractions(client);
    }

    @Test @Timeout(10) void concurrentRequestsIssueOnePostAndExposeProcessingState() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(client.confirm(any(), anyLong(), anyString())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture deadline");
            return TossPaymentsClient.Reply.payment(done());
        });
        try (var pool = Executors.newFixedThreadPool(8)) {
            var first = pool.submit(() -> service.confirm(request));
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                var followers = new ArrayList<Future<PaymentConfirmationResponse>>();
                for (int i = 0; i < 24; i++) followers.add(pool.submit(() -> service.confirm(request)));
                for (var follower : followers) {
                    var response = follower.get(2, TimeUnit.SECONDS);
                    assertThat(response.paymentStatus()).isEqualTo("CONFIRMING");
                    assertThat(response.completed()).isFalse();
                }
            } finally { release.countDown(); }
            assertThat(first.get(2, TimeUnit.SECONDS).completed()).isTrue();
        }
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
    }

    @Test void transportUncertaintyIsRecoveredByGetOnlyAndThenCached() {
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.failure(0, "TRANSPORT_ERROR", true));
        when(client.find(request.paymentKey())).thenReturn(TossPaymentsClient.Reply.payment(done()));
        assertThat(service.confirm(request).paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(service.confirm(request).completed()).isTrue();
        assertThat(service.confirm(request).completed()).isTrue();
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
        verify(client, times(1)).find(request.paymentKey());
    }

    @Test void lookup404DoesNotProveFailureOrAllowAnotherApproval() {
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.failure(503, "PROVIDER_ERROR", true));
        when(client.find(anyString())).thenReturn(TossPaymentsClient.Reply.failure(404, "NOT_FOUND_PAYMENT", false));
        for (int i = 0; i < 4; i++) assertThat(service.confirm(request).paymentStatus()).isEqualTo("UNKNOWN");
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
        verify(client, times(3)).find(request.paymentKey());
    }

    @ParameterizedTest @ValueSource(strings = {"key", "order", "amount", "currency", "status", "method", "unknown-method", "time"})
    void mismatchedProviderResponseNeverBecomesCompleted(String field) {
        var p = done();
        var altered = new TossPaymentResponse(field.equals("key") ? "other" : p.paymentKey(),
                field.equals("order") ? "other" : p.orderId(), field.equals("amount") ? 1 : p.totalAmount(),
                field.equals("currency") ? "USD" : p.currency(), field.equals("status") ? "IN_PROGRESS" : p.status(),
                field.equals("method") ? null : field.equals("unknown-method") ? "untrusted-provider-text" : p.method(),
                field.equals("time") ? null : p.approvedAt());
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.payment(altered));
        assertThat(service.confirm(request).paymentStatus()).isEqualTo("UNKNOWN");
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
    }

    @Test void virtualAccountIssuanceIsNotCompleted() {
        var p = done();
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.payment(
                new TossPaymentResponse(p.paymentKey(), p.orderId(), p.totalAmount(), "KRW", "WAITING_FOR_DEPOSIT", "가상계좌", null)));
        var response = service.confirm(request);
        assertThat(response.paymentStatus()).isEqualTo("WAITING_FOR_DEPOSIT");
        assertThat(response.completed()).isFalse();
        assertThat(response.pending()).isFalse();
        assertThat(service.confirm(request)).isEqualTo(response);
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
    }

    @Test void differentKeyOrAmountAndCrossOrderKeyReuseAreRejected() {
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.payment(done()));
        service.confirm(request);
        assertCode(() -> service.confirm(new PaymentConfirmationRequest("other_key", request.orderId(), 10000L)), "PAYMENT_REQUEST_CONFLICT");
        assertCode(() -> service.confirm(new PaymentConfirmationRequest(request.paymentKey(), request.orderId(), 20000L)), "PAYMENT_REQUEST_CONFLICT");
        assertCode(() -> service.confirm(new PaymentConfirmationRequest(request.paymentKey(), orders.create().orderId(), 10000L)), "PAYMENT_REQUEST_CONFLICT");
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
    }

    @Test void definiteRejectionIsNotBlindlyRetried() {
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.failure(400, "EXPIRED_PAYMENT", false));
        assertCode(() -> service.confirm(request), "PAYMENT_REJECTED");
        assertCode(() -> service.confirm(request), "PAYMENT_REJECTED");
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
        verify(client, never()).find(anyString());
    }

    @Test void providerRuntimeFailureCannotLeaveTheOrderPermanentlyBusy() {
        when(client.confirm(any(), anyLong(), anyString())).thenThrow(new IllegalStateException("private provider detail"));
        when(client.find(anyString())).thenReturn(TossPaymentsClient.Reply.payment(done()));
        assertThat(service.confirm(request).paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(service.confirm(request).completed()).isTrue();
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
        verify(client, times(1)).find(request.paymentKey());
    }

    @Test @Timeout(10) void simultaneousRecoveryRequestsShareOneLookup() throws Exception {
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.failure(0, "TRANSPORT_ERROR", true));
        service.confirm(request);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(client.find(anyString())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture deadline");
            return TossPaymentsClient.Reply.payment(done());
        });
        try (var pool = Executors.newFixedThreadPool(8)) {
            var leader = pool.submit(() -> service.confirm(request));
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                var followers = new ArrayList<Future<PaymentConfirmationResponse>>();
                for (int i=0; i<24; i++) followers.add(pool.submit(() -> service.confirm(request)));
                for (var follower : followers) assertThat(follower.get(2, TimeUnit.SECONDS).paymentStatus()).isEqualTo("UNKNOWN");
            } finally { release.countDown(); }
            assertThat(leader.get(2, TimeUnit.SECONDS).completed()).isTrue();
        }
        verify(client, times(1)).find(request.paymentKey());
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
    }

    @Test void cancelledProviderStateIsNotCompletedOrReapproved() {
        var p=done();
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.payment(
                new TossPaymentResponse(p.paymentKey(), p.orderId(), p.totalAmount(), p.currency(), "CANCELED", p.method(), p.approvedAt())));
        assertCode(() -> service.confirm(request), "PAYMENT_STATE_CONFLICT");
        assertCode(() -> service.confirm(request), "PAYMENT_STATE_CONFLICT");
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
    }

    @Test void existingProcessedPaymentIsReconciledRatherThanPostedAgain() {
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.failure(409, "ALREADY_PROCESSED_PAYMENT", true));
        when(client.find(anyString())).thenReturn(TossPaymentsClient.Reply.payment(done()));
        assertThat(service.confirm(request).paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(service.confirm(request).completed()).isTrue();
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
    }

    @Test void unresolvedEntriesCannotBeEvictedByCapacityPressure() {
        service = new DevPaymentConfirmationService(orders, new InMemoryPaymentConfirmationStore(1), client);
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.failure(0, "TRANSPORT_ERROR", true));
        service.confirm(request);
        assertCode(() -> service.confirm(new PaymentConfirmationRequest("other_key", orders.create().orderId(), 10000L)), "PAYMENT_CONFIRM_CAPACITY_EXCEEDED");
        verify(client, times(1)).confirm(any(), anyLong(), anyString());
    }

    @Test void recheckingAnUnknownApprovalUsesPinnedOrderEvenAfterDevOrderExpiry() {
        var source = mock(DevTossOrderService.class);
        when(source.find(request.orderId())).thenReturn(new DevTossOrder(request.orderId(), "fixture", 10000, "KRW", "PENDING"))
                .thenThrow(new IllegalStateException("order expired"));
        service = new DevPaymentConfirmationService(source, new InMemoryPaymentConfirmationStore(1), client);
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.failure(0, "TRANSPORT_ERROR", true));
        when(client.find(anyString())).thenReturn(TossPaymentsClient.Reply.payment(done()));
        service.confirm(request);
        assertThat(service.confirm(request).completed()).isTrue();
        verify(source, times(1)).find(request.orderId());
    }

    @Test void contradictoryWaitingResponseIsUnknownAndRecoveredByGetOnly() {
        var p=done();
        when(client.confirm(any(), anyLong(), anyString())).thenReturn(TossPaymentsClient.Reply.payment(
                new TossPaymentResponse(p.paymentKey(),p.orderId(),p.totalAmount(),"KRW","WAITING_FOR_DEPOSIT","가상계좌",p.approvedAt())));
        when(client.find(anyString())).thenReturn(TossPaymentsClient.Reply.payment(done()));
        assertThat(service.confirm(request).paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(service.confirm(request).completed()).isTrue();
        verify(client,times(1)).confirm(any(),anyLong(),anyString());
        verify(client,times(1)).find(request.paymentKey());
    }

    @Test void recoveryRuntimeFailureRemainsUnknownAndDoesNotStickBusy() {
        when(client.confirm(any(),anyLong(),anyString())).thenReturn(TossPaymentsClient.Reply.failure(0,"TRANSPORT_ERROR",true));
        when(client.find(anyString())).thenThrow(new IllegalStateException("private lookup detail"))
                .thenReturn(TossPaymentsClient.Reply.payment(done()));
        assertThat(service.confirm(request).paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(service.confirm(request).paymentStatus()).isEqualTo("UNKNOWN");
        assertThat(service.confirm(request).completed()).isTrue();
        verify(client,times(1)).confirm(any(),anyLong(),anyString());
        verify(client,times(2)).find(request.paymentKey());
    }

    @ParameterizedTest @ValueSource(ints={401,403,408,429,503})
    void recoveryErrorsNeverBecomeDefiniteFailureOrAnotherPost(int status) {
        when(client.confirm(any(),anyLong(),anyString())).thenReturn(TossPaymentsClient.Reply.failure(0,"TRANSPORT_ERROR",true));
        when(client.find(anyString())).thenReturn(TossPaymentsClient.Reply.failure(status,"PROVIDER_ERROR",status>=500));
        for(int i=0;i<3;i++) {
            var result=service.confirm(request);
            assertThat(result.paymentStatus()).isEqualTo("UNKNOWN");
            assertThat(result.completed()).isFalse();
        }
        verify(client,times(1)).confirm(any(),anyLong(),anyString());
        verify(client,times(2)).find(request.paymentKey());
    }

    private TossPaymentResponse done() {
        return new TossPaymentResponse(request.paymentKey(), request.orderId(), 10000, "KRW", "DONE", "카드",
                OffsetDateTime.parse("2026-11-16T10:00:00+09:00"));
    }
    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PaymentConfirmationException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }
}
