package com.smartlibrary.event;

import java.time.LocalDate;

public record PaymentConfirmationEvent(

        Long paymentId,

        String memberName,

        String email,

        String planName,

        String transactionId,

        String paymentMethod,

        long originalPaise,

        long discountPaise,

        long paidPaise,

        LocalDate startDate,

        LocalDate endDate

) {
}