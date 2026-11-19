package com.green.keyboard_project.service.payment;

import com.green.keyboard_project.dto.payment.TossPaymentResponse;
import com.green.keyboard_project.entity.Order;
import com.green.keyboard_project.entity.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.ZoneId;
import java.util.Objects;

/** 브라우저 입력이 아닌 서버가 검증한 외부 승인 결과를 저장하는 경계입니다.
 * 현재 API와 개발 주문에는 연결하지 않으며 실제 주문 권한 검증과 연결은 11단계 대상입니다.
 */
@Service
public class ConfirmedPaymentWriter {
    private final EntityManager em;
    private final TransactionTemplate transaction;

    // 주문과 결제를 별도의 원자적 트랜잭션으로 저장하도록 준비합니다. 이 서비스는 현재 개발 승인 API에서 호출하지 않으며 실제 주문 연결은 11단계 대상입니다.
    public ConfirmedPaymentWriter(EntityManager em, PlatformTransactionManager manager) {
        this.em = em;
        this.transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public enum Outcome { SAVED, ALREADY_SAVED }

    // 서버가 확인한 승인 완료 결과만 기존 주문에 저장합니다. 저장 실패는 이미 성공한 승인의 실패를 뜻하지 않으므로 재승인하면 안 됩니다.
    public Outcome saveDone(int existingOrderId, TossPaymentResponse verified) {
        validate(verified);
        try {
            return Objects.requireNonNull(transaction.execute(status -> write(existingOrderId, verified)));
        } catch (PersistenceProblem problem) {
            throw problem;
        } catch (RuntimeException failure) {
            // 예외에 결제 식별자·SQL·인증정보를 넣지 않아 저장 실패 시에도 민감정보를 숨깁니다.
            throw new PersistenceProblem("APPROVED_PERSISTENCE_PENDING");
        }
    }

    // 주문 행을 잠근 뒤 기존 결제와 대조합니다. 같은 결과는 재사용하고 준비된 결제만 완료로 바꾸며 주문 상태도 함께 저장합니다.
    private Outcome write(int id, TossPaymentResponse p) {
        // 같은 주문에 대한 동시 저장을 직렬화하고, 브라우저 입력이 아닌 실제 주문의 최종금액과 승인 결과를 대조합니다.
        Order order = em.find(Order.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (order == null) throw new PersistenceProblem("ORDER_NOT_FOUND");
        if (!p.orderId().equals(order.getOrderNumber()) || p.totalAmount() != order.getFinalPayAmount())
            throw new PersistenceProblem("ORDER_PAYMENT_MISMATCH");
        var rows = em.createQuery("select p from Payment p where p.order.orderId = :id", Payment.class)
                .setParameter("id", id).getResultList();
        var method = method(p.method());
        var paidAt = p.approvedAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime().withNano(0);
        if (!rows.isEmpty()) {
            Payment saved = rows.getFirst();
            if (rows.size() == 1 && "TOSS".equals(saved.getPgProvider())
                    && p.paymentKey().equals(saved.getPgTid()) && saved.getPaidAmount() == p.totalAmount()
                    && saved.getPaymentMethod() == method && saved.getPaymentStatus() == Payment.PaymentStatusType.PAID
                    && Objects.equals(saved.getPaidAt(), paidAt) && saved.getCancelledAt() == null
                    && order.getOrderStatus() == Order.OrderStatusType.ORDERED)
                return Outcome.ALREADY_SAVED;
            // 기존 준비 결제는 기록된 값이 모두 일치할 때만 완료로 전환할 수 있습니다.
            if (rows.size() == 1 && order.getOrderStatus() == Order.OrderStatusType.PENDING
                    && "TOSS".equals(saved.getPgProvider()) && saved.getPaymentStatus() == Payment.PaymentStatusType.READY
                    && saved.getPaidAmount() == p.totalAmount() && saved.getPaymentMethod() == method
                    && (saved.getPgTid() == null || p.paymentKey().equals(saved.getPgTid()))
                    && saved.getPaidAt() == null && saved.getCancelledAt() == null) {
                saved.setPgTid(p.paymentKey()); saved.setPaymentStatus(Payment.PaymentStatusType.PAID);
                saved.setPaidAt(paidAt);
                order.setOrderStatus(Order.OrderStatusType.ORDERED); order.setOrderedAt(paidAt);
                em.flush();
                return Outcome.SAVED;
            }
            throw new PersistenceProblem("PAYMENT_RECORD_CONFLICT");
        }
        if (order.getOrderStatus() != Order.OrderStatusType.PENDING)
            throw new PersistenceProblem("ORDER_STATE_CONFLICT");
        // 새 결제와 주문 완료 상태를 같은 트랜잭션에 반영합니다. 중간에 오류가 나면 둘 다 롤백됩니다.
        Payment payment = new Payment();
        payment.setOrder(order); payment.setPaymentMethod(method); payment.setPgProvider("TOSS");
        payment.setPgTid(p.paymentKey()); payment.setPaymentStatus(Payment.PaymentStatusType.PAID);
        payment.setPaidAmount(Math.toIntExact(p.totalAmount())); payment.setPaidAt(paidAt);
        em.persist(payment);
        order.setOrderStatus(Order.OrderStatusType.ORDERED); order.setOrderedAt(paidAt);
        em.flush();
        return Outcome.SAVED;
    }

    // 개발 메모리 주문과 미완료 상태를 DB 저장에서 제외합니다. 현재 컬럼 길이를 넘는 키는 자르지 않고 거부합니다.
    private static void validate(TossPaymentResponse p) {
        if (p == null || !"DONE".equals(p.status())) throw new PersistenceProblem("DONE_REQUIRED");
        if (p.orderId() == null || p.orderId().startsWith("dev_") || !p.orderId().matches("[A-Za-z0-9_=-]{6,32}"))
            throw new PersistenceProblem("REAL_ORDER_REQUIRED");
        if (p.paymentKey() == null || !p.paymentKey().matches("[!-~]{1,100}"))
            throw new PersistenceProblem("PAYMENT_KEY_SCHEMA_LIMIT");
        if (!"KRW".equals(p.currency()) || p.totalAmount() <= 0 || p.totalAmount() > Integer.MAX_VALUE || p.approvedAt() == null)
            throw new PersistenceProblem("INVALID_APPROVAL_RESULT");
        method(p.method());
    }

    // 현재 DB가 표현할 수 있는 결제수단만 매핑하고 지원하지 않는 수단을 다른 값으로 대체하지 않습니다.
    private static Payment.PaymentMethodType method(String value) {
        if (value == null) throw new PersistenceProblem("UNSUPPORTED_PAYMENT_METHOD");
        return switch (value) {
            case "카드" -> Payment.PaymentMethodType.CARD;
            case "계좌이체" -> Payment.PaymentMethodType.TRANSFER;
            case "간편결제" -> Payment.PaymentMethodType.EASY_PAY;
            case "가상계좌" -> Payment.PaymentMethodType.VIRTUAL_ACCOUNT;
            default -> throw new PersistenceProblem("UNSUPPORTED_PAYMENT_METHOD");
        };
    }

    public static final class PersistenceProblem extends RuntimeException {
        // 저장 경계의 안전한 오류 코드만 전달하여 SQL·결제 키 같은 내부 정보를 숨깁니다.
        public PersistenceProblem(String code) { super(code); }
    }
}
