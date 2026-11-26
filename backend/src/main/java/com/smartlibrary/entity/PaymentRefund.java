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
        name = "payment_refunds",
        indexes = {
                @Index(
                        name = "idx_refund_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_refund_user",
                        columnList = "user_id"
                )
        }
)
public class PaymentRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long paymentId;

    @Column(nullable = false, unique = true)
    private Long paymentOrderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long requestedBy;

    @Column(nullable = false, unique = true, length = 80)
    private String refundReference;

    @Column(nullable = false, length = 100)
    private String razorpayPaymentId;

    @Column(unique = true, length = 100)
    private String razorpayRefundId;

    @Column(nullable = false)
    private long amountPaise;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RefundStatus status = RefundStatus.REQUESTED;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant processedAt;

    private Instant lastCheckedAt;

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
}