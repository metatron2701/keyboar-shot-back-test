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
@Table(name = "orders", uniqueConstraints = {@UniqueConstraint(columnNames = {"order_number"})})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id", nullable = false)
    private int orderId;

    @Column(name = "order_number", nullable = false, length = 32)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "orderer_name", nullable = false, length = 50)
    private String ordererName;

    @Column(name = "orderer_phone", nullable = false, length = 11)
    private String ordererPhone;

    @Column(name = "orderer_email", nullable = false, length = 100)
    private String ordererEmail;

    @Column(name = "order_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatusType orderStatus;

    @Column(name = "total_product_amount", nullable = false)
    private int totalProductAmount;

    @Column(name = "delivery_fee", nullable = false)
    private int deliveryFee;

    @Column(name = "final_pay_amount", nullable = false)
    private int finalPayAmount;

    @Column(name = "ordered_at", nullable = true)
    private LocalDateTime orderedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public enum OrderStatusType {
        PENDING, ORDERED, CANCELLED
    }

}
