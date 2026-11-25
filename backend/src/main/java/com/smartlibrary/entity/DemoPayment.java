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
        name = "demo_payments",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {
                        "user_id",
                        "coupon_code"
                }
        )
)
public class DemoPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_code", nullable = false)
    private String couponCode;

    private long originalPaise;

    private long discountPaise;

    private long paidPaise;

    private Long membershipId;

    private Instant createdAt;

    // ==========================================
    // TRANSACTION REFERENCE
    // ==========================================

    @Transient
    public String getTransactionId() {

        if (id == null) {
            return null;
        }

        return String.format(
                "SLM-DEMO-%08d",
                id
        );
    }

    // ==========================================
    // PAYMENT STATUS
    // ==========================================

    @Transient
    public String getStatus() {

        return isFreeRedemption()
                ? "SUCCESS"
                : "UNVERIFIED";
    }

    // ==========================================
    // PAYMENT METHOD
    // ==========================================

    @Transient
    public String getPaymentMethod() {

        return "DEMO_COUPON";
    }

    // ==========================================
    // FREE REDEMPTION CHECK
    // ==========================================

    @Transient
    public boolean isFreeRedemption() {

        return originalPaise > 0

                && discountPaise == originalPaise

                && paidPaise == 0

                && membershipId != null;
    }
}