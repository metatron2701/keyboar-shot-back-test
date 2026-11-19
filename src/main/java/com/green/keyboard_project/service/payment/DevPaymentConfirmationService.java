package com.green.keyboard_project.service.payment;

import com.green.keyboard_project.config.tosspayments.TossPaymentsClient;
import com.green.keyboard_project.dev.tossorders.DevTossOrderService;
import com.green.keyboard_project.dto.payment.*;
import com.green.keyboard_project.exceptions.PaymentConfirmationException;

import java.util.Set;

/** 개발 승인 흐름에서는 회원 서비스·주문 저장소·DB 변경 트랜잭션을 사용하지 않습니다. */
public final class DevPaymentConfirmationService {
    private static final Set<String> METHODS = Set.of("카드", "가상계좌", "간편결제", "휴대폰", "계좌이체",
            "문화상품권", "도서문화상품권", "게임문화상품권");
    private final DevTossOrderService orders;
    private final InMemoryPaymentConfirmationStore store;
    private final TossPaymentsClient client;

    // 개발 주문·중복 방지 기록·외부 통신을 연결합니다. 실제 주문 DB 저장 서비스와는 별도 흐름입니다.
    public DevPaymentConfirmationService(DevTossOrderService orders, InMemoryPaymentConfirmationStore store,
                                         TossPaymentsClient client) {
        this.orders = orders; this.store = store; this.client = client;
    }

    // 입력과 서버 주문을 확인한 뒤 최초 요청만 승인하고, 중복 요청은 기존 결과 또는 조회 전용 처리를 사용합니다.
    public PaymentConfirmationResponse confirm(PaymentConfirmationRequest request) {
        if (request == null || !request.valid()) throw new PaymentConfirmationException(400,
                "INVALID_CONFIRM_REQUEST", "승인 요청의 필수값과 형식을 확인해 주세요.");
        // 처리 권한과 기존 결과를 먼저 확인해 같은 주문의 중복 승인 요청을 막습니다.
        var lease = store.acquire(request, () -> orders.getDevTestOrder(request.orderId()));
        if (lease.immediate() != null) return lease.immediate();
        try {
            // 미확정 요청은 외부 결과 조회만 수행하여 네트워크 오류 뒤에 같은 결제를 다시 승인하는 일을 막습니다.
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
            // 외부 결과의 결제 키·주문번호·금액·통화를 서버 기준과 대조한 뒤에만 상태를 인정합니다.
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
            // 입금 대기와 승인 완료를 구분합니다. 완료 시각이 없거나 대기 응답이 모순되면 미확정으로 남깁니다.
            boolean completed = "DONE".equals(payment.status());
            if (payment.method() == null || !METHODS.contains(payment.method())
                    || (completed && payment.approvedAt() == null)
                    || (!completed && (!"가상계좌".equals(payment.method()) || payment.approvedAt() != null))) return unknown(lease, request);
            var response = new PaymentConfirmationResponse(request.orderId(), lease.serverAmount(), "KRW",
                    payment.status(), completed, payment.method(), payment.approvedAt(), "MEMORY_ONLY");
            // 검증한 결과를 메모리에 남겨 같은 요청이 다시 들어오면 외부 승인 없이 재사용합니다.
            store.complete(lease, response);
            return response;
        } catch (PaymentConfirmationException error) {
            throw error;
        } catch (RuntimeException error) {
            // 승인 처리에 진입한 뒤 통신·응답 해석·실행 오류가 나더라도 실제 승인이 실패했다고 단정할 수 없습니다.
            return unknown(lease, request);
        }
    }

    // 결과를 확정할 수 없다는 사실을 기록해 다음 요청이 재승인 대신 조회로 이어지게 합니다.
    private PaymentConfirmationResponse unknown(InMemoryPaymentConfirmationStore.Lease lease,
                                                  PaymentConfirmationRequest request) {
        store.unknown(lease);
        return PaymentConfirmationResponse.pending(request, "UNKNOWN");
    }
}
