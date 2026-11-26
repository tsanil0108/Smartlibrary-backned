package com.smartlibrary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PaymentDto {

    private PaymentDto() {
    }

    // Coupon is optional.

    public record QuoteRequest(

            @Size(max = 40)
            String code

    ) {
    }

    public record QuoteResponse(

            String couponCode,

            long originalPaise,

            long discountPaise,

            long payablePaise,

            int discountPercent,

            String currency,

            String planName,

            int membershipDays,

            boolean freeRedemption,

            boolean onlinePaymentAvailable

    ) {
    }

    public record CreateOrderRequest(

            @Size(max = 40)
            String code

    ) {
    }

    public record CreateOrderResponse(

            Long orderId,

            String orderReference,

            String razorpayOrderId,

            String razorpayKeyId,

            String couponCode,

            long originalPaise,

            long discountPaise,

            long payablePaise,

            String currency,

            String planName,

            int membershipDays,

            String memberName,

            String memberEmail,

            String status

    ) {
    }

    public record RedeemRequest(

            @NotBlank
            @Size(max = 40)
            String code

    ) {
    }

    public record VerifyRequest(

            @NotNull
            Long orderId,

            @NotBlank
            @Size(max = 100)
            String razorpayOrderId,

            @NotBlank
            @Size(max = 100)
            String razorpayPaymentId,

            @NotBlank
            @Size(max = 64)
            String razorpaySignature

    ) {
    }

    public record PaymentResult(

            Long paymentId,

            Long orderId,

            Long membershipId,

            String transactionId,

            String status,

            String paymentMethod,

            long paidPaise,

            String message

    ) {
    }
}