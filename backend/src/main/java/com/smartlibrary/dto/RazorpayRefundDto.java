package com.smartlibrary.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class RazorpayRefundDto {

    private RazorpayRefundDto() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundResponse(

            String id,

            String entity,

            long amount,

            String currency,

            @JsonProperty("payment_id")
            String paymentId,

            String receipt,

            String status,

            @JsonProperty("speed_requested")
            String speedRequested,

            @JsonProperty("speed_processed")
            String speedProcessed,

            @JsonProperty("created_at")
            long createdAt

    ) {

        public boolean isProcessed() {

            return "processed".equalsIgnoreCase(status);
        }

        public boolean isPending() {

            return "pending".equalsIgnoreCase(status);
        }

        public boolean isFailed() {

            return "failed".equalsIgnoreCase(status);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefundCollection(

            String entity,

            int count,

            List<RefundResponse> items

    ) {
    }
}