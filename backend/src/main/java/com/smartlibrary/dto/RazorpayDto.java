package com.smartlibrary.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class RazorpayDto {

    private RazorpayDto() {
    }

    // ==========================================
    // CREATE ORDER RESPONSE
    // ==========================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderResponse(

            String id,

            String entity,

            long amount,

            @JsonProperty("amount_paid")
            long amountPaid,

            @JsonProperty("amount_due")
            long amountDue,

            String currency,

            String receipt,

            String status,

            int attempts,

            @JsonProperty("created_at")
            long createdAt

    ) {
    }

    // ==========================================
    // PAYMENT DETAILS RESPONSE
    // ==========================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentResponse(

            String id,

            String entity,

            long amount,

            String currency,

            String status,

            @JsonProperty("order_id")
            String orderId,

            String method,

            boolean captured,

            String email,

            String contact,

            @JsonProperty("amount_refunded")
            long amountRefunded,

            @JsonProperty("refund_status")
            String refundStatus,

            @JsonProperty("error_code")
            String errorCode,

            @JsonProperty("error_description")
            String errorDescription,

            @JsonProperty("created_at")
            long createdAt

    ) {

        public boolean isCaptured() {

            return captured
                    && "captured".equalsIgnoreCase(status);
        }

        public boolean isAuthorized() {

            return "authorized".equalsIgnoreCase(status);
        }

        public boolean isFailed() {

            return "failed".equalsIgnoreCase(status);
        }
    }
}