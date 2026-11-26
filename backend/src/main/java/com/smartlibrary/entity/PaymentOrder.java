package com.smartlibrary.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "payment_orders",
        indexes = {
                @Index(
                        name = "idx_payment_order_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_payment_order_status",
                        columnList = "status"
                )
        }
)
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "user_id",
            nullable = false
    )
    private Long userId;

    @Column(
            nullable = false,
            unique = true,
            length = 80
    )
    private String orderReference;

    @Column(
            unique = true,
            length = 100
    )
    private String razorpayOrderId;

    @Column(
            unique = true,
            length = 100
    )
    private String razorpayPaymentId;

    @Column(length = 40)
    private String couponCode;

    @Column(nullable = false)
    private long originalPaise;

    @Column(nullable = false)
    private long discountPaise;

    @Column(nullable = false)
    private long payablePaise;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false)
    private int membershipDays = 30;

    @Column(nullable = false, length = 100)
    private String planName = "Premium Monthly";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentOrderStatus status =
            PaymentOrderStatus.CREATED;

    @Column(length = 30)
    private String paymentMethod;

    @Column(unique = true)
    private Long membershipId;

    @Column(unique = true)
    private Long completedPaymentId;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant expiresAt;

    private Instant capturedAt;

    private Instant verifiedAt;

    private Instant completedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {

        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt = Instant.now();
    }

    @Transient
    public boolean isFreeOrder() {

        return originalPaise > 0
                && discountPaise == originalPaise
                && payablePaise == 0;
    }

    @Transient
    public boolean isCompleted() {

        return status == PaymentOrderStatus.SUCCESS
                && membershipId != null
                && completedPaymentId != null;
    }
}