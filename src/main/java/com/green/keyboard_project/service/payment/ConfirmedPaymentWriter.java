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

/** Persistence boundary for a trusted, server-verified provider result, not a browser DTO.
 * No endpoint or development-order integration: real-order authorization belongs to stage 11.
 */
@Service
public class ConfirmedPaymentWriter {
    private final EntityManager em;
    private final TransactionTemplate transaction;

    public ConfirmedPaymentWriter(EntityManager em, PlatformTransactionManager manager) {
        this.em = em;
        this.transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public enum Outcome { SAVED, ALREADY_SAVED }

    /** Never calls the provider. A failed commit means persistence is pending, not approval failed. */
    public Outcome saveDone(int existingOrderId, TossPaymentResponse verified) {
        validate(verified);
        try {
            return Objects.requireNonNull(transaction.execute(status -> write(existingOrderId, verified)));
        } catch (PersistenceProblem problem) {
            throw problem;
        } catch (RuntimeException failure) {
            // Deliberately exclude provider identifiers, SQL and credentials from the exception.
            throw new PersistenceProblem("APPROVED_PERSISTENCE_PENDING");
        }
    }

    private Outcome write(int id, TossPaymentResponse p) {
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
            // Existing READY reservations may be completed only if every recorded value agrees.
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
        Payment payment = new Payment();
        payment.setOrder(order); payment.setPaymentMethod(method); payment.setPgProvider("TOSS");
        payment.setPgTid(p.paymentKey()); payment.setPaymentStatus(Payment.PaymentStatusType.PAID);
        payment.setPaidAmount(Math.toIntExact(p.totalAmount())); payment.setPaidAt(paidAt);
        em.persist(payment);
        order.setOrderStatus(Order.OrderStatusType.ORDERED); order.setOrderedAt(paidAt);
        em.flush();
        return Outcome.SAVED;
    }

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
        public PersistenceProblem(String code) { super(code); }
    }
}
