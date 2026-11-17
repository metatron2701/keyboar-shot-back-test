package com.green.keyboard_project.service;

import com.green.keyboard_project.config.tosspayments.TossPaymentsClient;
import com.green.keyboard_project.dev.tossorders.DevTossOrderService;
import com.green.keyboard_project.dto.payment.*;
import com.green.keyboard_project.exceptions.PaymentConfirmationException;
import com.green.keyboard_project.service.payment.InMemoryPaymentConfirmationStore;
import java.util.Set;

/** No JPA repository, member service, transaction or order DB mutation. */
public final class DevPaymentConfirmationService {
    private static final Set<String> METHODS = Set.of("카드", "가상계좌", "간편결제", "휴대폰", "계좌이체",
            "문화상품권", "도서문화상품권", "게임문화상품권");
    private final DevTossOrderService orders;
    private final InMemoryPaymentConfirmationStore store;
    private final TossPaymentsClient client;

    public DevPaymentConfirmationService(DevTossOrderService orders, InMemoryPaymentConfirmationStore store,
                                         TossPaymentsClient client) {
        this.orders = orders; this.store = store; this.client = client;
    }

    public PaymentConfirmationResponse confirm(PaymentConfirmationRequest request) {
        if (request == null || !request.valid()) throw new PaymentConfirmationException(400,
                "INVALID_CONFIRM_REQUEST", "승인 요청의 필수값과 형식을 확인해 주세요.");
        var lease = store.acquire(request, () -> orders.find(request.orderId()));
        if (lease.immediate() != null) return lease.immediate();
        try {
            var reply = lease.lookupOnly() ? client.find(request.paymentKey())
                    : client.confirm(request, lease.serverAmount(), lease.idempotencyKey());
            if (reply.failure() != null) {
                if (lease.lookupOnly() || reply.failure().uncertain()) return unknown(lease, request);
                boolean configuration = reply.failure().status() == 401 || reply.failure().status() == 403
                        || "INVALID_API_KEY".equals(reply.failure().code()) || "UNAUTHORIZED_KEY".equals(reply.failure().code());
                var error = new PaymentConfirmationException(configuration ? 502 : 422,
                        configuration ? "PAYMENT_PROVIDER_CONFIGURATION_ERROR" : "PAYMENT_REJECTED",
                        configuration ? "결제 테스트 설정을 확인해 주세요." : "결제 승인이 거절되었습니다. 결제수단과 인증 상태를 확인하세요.",
                        reply.failure().code());
                store.fail(lease, error);
                throw error;
            }
            TossPaymentResponse payment = reply.payment();
            if (payment == null || !request.paymentKey().equals(payment.paymentKey())
                    || !request.orderId().equals(payment.orderId()) || lease.serverAmount() != payment.totalAmount()
                    || !"KRW".equals(payment.currency())) return unknown(lease, request);
            if (!"DONE".equals(payment.status()) && !"WAITING_FOR_DEPOSIT".equals(payment.status())) {
                if ("ABORTED".equals(payment.status()) || "EXPIRED".equals(payment.status())
                        || "CANCELED".equals(payment.status()) || "PARTIAL_CANCELED".equals(payment.status())) {
                    var error = new PaymentConfirmationException(409, "PAYMENT_STATE_CONFLICT", "결제 상태를 확인해 주세요. 다시 승인하지 않았습니다.");
                    store.fail(lease, error); throw error;
                }
                return unknown(lease, request);
            }
            boolean completed = "DONE".equals(payment.status());
            if (payment.method() == null || !METHODS.contains(payment.method())
                    || (completed && payment.approvedAt() == null)
                    || (!completed && (!"가상계좌".equals(payment.method()) || payment.approvedAt() != null))) return unknown(lease, request);
            var response = new PaymentConfirmationResponse(request.orderId(), lease.serverAmount(), "KRW",
                    payment.status(), completed, payment.method(), payment.approvedAt(), "MEMORY_ONLY");
            store.complete(lease, response);
            return response;
        } catch (PaymentConfirmationException error) {
            throw error;
        } catch (RuntimeException error) {
            // A transport/parse/runtime failure after acquisition cannot prove that approval failed.
            return unknown(lease, request);
        }
    }

    private PaymentConfirmationResponse unknown(InMemoryPaymentConfirmationStore.Lease lease,
                                                  PaymentConfirmationRequest request) {
        store.unknown(lease);
        return PaymentConfirmationResponse.pending(request, "UNKNOWN");
    }
}
