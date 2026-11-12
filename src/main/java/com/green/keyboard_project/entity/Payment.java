package com.green.keyboard_project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "payment", uniqueConstraints = {@UniqueConstraint(columnNames = {"order_id"})})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id", nullable = false)
    private int paymentId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "payment_method", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentMethodType paymentMethod;

    @Column(name = "pg_provider", nullable = false, length = 50)
    private String pgProvider;

    @Column(name = "pg_tid", nullable = true, length = 100)
    private String pgTid;

    @Column(name = "payment_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatusType paymentStatus;

    @Column(name = "paid_amount", nullable = false)
    private int paidAmount;

    @Column(name = "paid_at", nullable = true)
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at", nullable = true)
    private LocalDateTime cancelledAt;

    public enum PaymentMethodType {
        CARD, TRANSFER, EASY_PAY, VIRTUAL_ACCOUNT
    }

    public enum PaymentStatusType {
        READY, PAID, FAILED, CANCELLED
    }

}
