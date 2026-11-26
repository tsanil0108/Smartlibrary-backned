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

    @Column(
            name = "user_id",
            nullable = false
    )
    private Long userId;

    // Coupon is now optional.

    @Column(
            name = "coupon_code",
            length = 40
    )
    private String couponCode;

    private long originalPaise;

    private long discountPaise;

    private long paidPaise;

    private Long membershipId;

    private Instant createdAt;

    // ==========================================
    // REAL PAYMENT FIELDS
    // ==========================================

    @Column(unique = true)
    private Long paymentOrderId;

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

    @Column(
            name = "payment_method",
            length = 30
    )
    private String storedPaymentMethod;

    @Column(
            name = "payment_status",
            length = 30
    )
    private String storedStatus;

    @Column(length = 3)
    private String currency = "INR";

    private Instant verifiedAt;

    // ==========================================
    // TRANSACTION REFERENCE
    // ==========================================

    @Transient
    public String getTransactionId() {

        if (id == null) {
            return null;
        }

        if (razorpayPaymentId != null
                && !razorpayPaymentId.isBlank()) {

            return razorpayPaymentId;
        }

        if ("COUPON".equals(storedPaymentMethod)) {

            return String.format(
                    "SLM-CPN-%08d",
                    id
            );
        }

        // Preserve old transaction references.

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

        if (storedStatus != null
                && !storedStatus.isBlank()) {

            return storedStatus;
        }

        // Backward compatibility for old payments.

        return isFreeRedemption()
                ? "SUCCESS"
                : "UNVERIFIED";
    }

    // ==========================================
    // PAYMENT METHOD
    // ==========================================

    @Transient
    public String getPaymentMethod() {

        if (storedPaymentMethod != null
                && !storedPaymentMethod.isBlank()) {

            return storedPaymentMethod;
        }

        return "DEMO_COUPON";
    }

    // ==========================================
    // FREE COUPON REDEMPTION
    // ==========================================

    @Transient
    public boolean isFreeRedemption() {

        return originalPaise > 0

                && discountPaise == originalPaise

                && paidPaise == 0

                && membershipId != null;
    }

    // ==========================================
    // VERIFIED ONLINE PAYMENT
    // ==========================================

    @Transient
    public boolean isVerifiedOnlinePayment() {

        return paidPaise > 0

                && paidPaise ==
                originalPaise - discountPaise

                && razorpayPaymentId != null

                && !razorpayPaymentId.isBlank()

                && verifiedAt != null

                && membershipId != null

                && "SUCCESS".equals(storedStatus);
    }

    // ==========================================
    // COMPLETED PAYMENT
    // ==========================================

    @Transient
    public boolean isSuccessfulPayment() {

        return "SUCCESS".equals(getStatus())

                && (
                isFreeRedemption()
                        || isVerifiedOnlinePayment()
        );
    }
}