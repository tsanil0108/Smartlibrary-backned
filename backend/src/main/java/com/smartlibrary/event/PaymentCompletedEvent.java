package com.smartlibrary.event;

import java.time.LocalDate;

public record PaymentCompletedEvent(

        Long paymentId,

        Long userId,

        String email,

        String memberName,

        String transactionId,

        String couponCode,

        long originalPaise,

        long discountPaise,

        long paidPaise,

        String membershipPlan,

        LocalDate membershipStartDate,

        LocalDate membershipEndDate

) {
}