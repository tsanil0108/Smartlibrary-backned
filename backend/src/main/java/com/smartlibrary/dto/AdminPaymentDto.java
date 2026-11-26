package com.smartlibrary.dto;

import com.smartlibrary.entity.PaymentRefund;
import com.smartlibrary.entity.RefundStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AdminPaymentDto {

    private AdminPaymentDto() {
    }

    // ==========================================
    // PAYMENT STATISTICS
    // ==========================================

    public record PaymentSummary(

            long totalTransactions,

            long successfulPayments,

            long onlinePayments,

            long couponRedemptions,

            long pendingOrders,

            long refundPending,

            long refundedPayments,

            long grossRevenuePaise,

            long totalDiscountPaise,

            long totalRefundedPaise,

            long netRevenuePaise

    ) {
    }

    // ==========================================
    // ADMIN REFUND REQUEST
    // ==========================================

    public record RefundRequest(

            @NotBlank(
                    message = "Refund reason is required."
            )
            @Size(
                    min = 5,
                    max = 500,
                    message = "Reason must contain 5 to 500 characters."
            )
            String reason

    ) {
    }

    // ==========================================
    // REFUND RESPONSE
    // ==========================================

    public record RefundResponse(

            Long id,

            Long paymentId,

            Long paymentOrderId,

            Long userId,

            Long requestedBy,

            String refundReference,

            String razorpayPaymentId,

            String razorpayRefundId,

            long amountPaise,

            String currency,

            String reason,

            RefundStatus status,

            String failureReason,

            Instant createdAt,

            Instant processedAt,

            Instant lastCheckedAt

    ) {

        public static RefundResponse from(
                PaymentRefund refund
        ) {

            return new RefundResponse(

                    refund.getId(),

                    refund.getPaymentId(),

                    refund.getPaymentOrderId(),

                    refund.getUserId(),

                    refund.getRequestedBy(),

                    refund.getRefundReference(),

                    refund.getRazorpayPaymentId(),

                    refund.getRazorpayRefundId(),

                    refund.getAmountPaise(),

                    refund.getCurrency(),

                    refund.getReason(),

                    refund.getStatus(),

                    refund.getFailureReason(),

                    refund.getCreatedAt(),

                    refund.getProcessedAt(),

                    refund.getLastCheckedAt()
            );
        }
    }

    // ==========================================
    // PAYMENT RECONCILIATION RESPONSE
    // ==========================================

    public record ReconciliationResponse(

            Long orderId,

            String orderReference,

            String status,

            String message

    ) {
    }
}