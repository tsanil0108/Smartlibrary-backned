package com.smartlibrary.dto;

import java.time.Instant;
import java.time.LocalDate;

public final class PaymentReceiptDto {

    private PaymentReceiptDto() {
    }

    public record ReceiptResponse(

            Long paymentId,

            String receiptNumber,

            String transactionId,

            Long userId,

            String memberName,

            String email,

            String couponCode,

            long originalPaise,

            long discountPaise,

            long paidPaise,

            Long membershipId,

            String membershipPlan,

            LocalDate membershipStartDate,

            LocalDate membershipEndDate,

            String paymentMethod,

            String paymentStatus,

            Instant paymentDate

    ) {
    }
}