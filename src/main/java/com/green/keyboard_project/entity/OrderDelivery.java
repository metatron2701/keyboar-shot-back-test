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
@Table(name = "order_delivery", uniqueConstraints = {@UniqueConstraint(columnNames = {"order_id"})})
public class OrderDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_id", nullable = false)
    private int deliveryId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "delivery_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private DeliveryStatusType deliveryStatus;

    @Column(name = "tracking_company", nullable = true, length = 50)
    private String trackingCompany;

    @Column(name = "tracking_number", nullable = true, length = 100)
    private String trackingNumber;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "road_address", nullable = false, length = 255)
    private String roadAddress;

    @Column(name = "detail_address", nullable = false, length = 255)
    private String detailAddress;

    @Column(name = "delivery_memo", nullable = true, length = 255)
    private String deliveryMemo;

    @Column(name = "shipped_at", nullable = true)
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at", nullable = true)
    private LocalDateTime deliveredAt;

    public enum DeliveryStatusType {
        PREPARING, SHIPPING, DELIVERED, RETURNED
    }

}
