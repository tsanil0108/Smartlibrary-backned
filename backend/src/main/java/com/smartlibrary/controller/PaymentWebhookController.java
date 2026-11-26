package com.smartlibrary.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartlibrary.entity.PaymentOrderStatus;

import com.smartlibrary.repository.PaymentOrderRepository;

import com.smartlibrary.service.PaymentCompletionService;
import com.smartlibrary.service.RazorpayService;
import com.smartlibrary.service.RefundReconciliationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final RazorpayService razorpayService;

    private final PaymentCompletionService completionService;

    private final RefundReconciliationService refundReconciliationService;

    private final PaymentOrderRepository orders;

    private final ObjectMapper objectMapper;

    @PostMapping("/razorpay")
    public ResponseEntity<Map<String, Object>> webhook(

            @RequestHeader(
                    value = "X-Razorpay-Signature",
                    required = false
            )
            String signature,

            @RequestBody byte[] rawBody

    ) {

        // Verify signature using original raw request body.

        if (!razorpayService.verifyWebhookSignature(

                rawBody,

                signature

        )) {

            return response(

                    401,

                    false,

                    "Invalid webhook signature."
            );
        }

        try {

            JsonNode root = objectMapper.readTree(rawBody);

            if (root == null || !root.isObject()) {

                return response(
                        400,
                        false,
                        "Invalid webhook payload."
                );
            }

            String event = root

                    .path("event")

                    .asText("");

            return switch (event) {

                case "payment.captured" ->

                        handlePaymentCaptured(root);

                case "refund.created",
                     "refund.processed",
                     "refund.failed" ->

                        handleRefundEvent(root);

                default -> response(

                        200,

                        true,

                        "Webhook received."
                );
            };

        } catch (IOException ex) {

            return response(

                    400,

                    false,

                    "Invalid webhook payload."
            );

        } catch (RuntimeException ex) {

            log.error(

                    "Razorpay webhook processing failed: {}",

                    ex.getClass().getSimpleName()
            );

            return response(

                    500,

                    false,

                    "Webhook processing failed."
            );
        }
    }

    // ==========================================
    // PAYMENT CAPTURED
    // ==========================================

    private ResponseEntity<Map<String, Object>>
    handlePaymentCaptured(JsonNode root) {

        JsonNode payment = root

                .path("payload")

                .path("payment")

                .path("entity");

        String paymentId = payment

                .path("id")

                .asText("");

        String orderId = payment

                .path("order_id")

                .asText("");

        if (paymentId.isBlank()

                || orderId.isBlank()) {

            return response(

                    400,

                    false,

                    "Invalid payment event."
            );
        }

        var order = orders

                .findByRazorpayOrderId(orderId)

                .orElse(null);

        if (order == null) {

            log.warn(

                    "Webhook received for unknown order: {}",

                    orderId
            );

            return response(

                    503,

                    false,

                    "Order not available."
            );
        }

        // Do not reactivate membership after a refund
        // when an older capture webhook is delivered again.

        if (order.getStatus() == PaymentOrderStatus.REFUND_PENDING

                || order.getStatus() == PaymentOrderStatus.REFUNDED) {

            if (!paymentId.equals(order.getRazorpayPaymentId())) {

                log.error(
                        "Unexpected captured payment on refunded order: {}",
                        orderId
                );

                return response(
                        409,
                        false,
                        "Payment requires manual investigation."
                );
            }

            return response(

                    200,

                    true,

                    "Payment was already processed."
            );
        }

        completionService.completeFromWebhook(

                orderId,

                paymentId
        );

        log.info(

                "Payment webhook processed: {}",

                paymentId
        );

        return response(

                200,

                true,

                "Payment webhook processed."
        );
    }

    // ==========================================
    // REFUND EVENTS
    // ==========================================

    private ResponseEntity<Map<String, Object>>
    handleRefundEvent(JsonNode root) {

        JsonNode refund = root

                .path("payload")

                .path("refund")

                .path("entity");

        String refundId = refund

                .path("id")

                .asText("");

        String paymentId = refund

                .path("payment_id")

                .asText("");

        if (refundId.isBlank()

                || paymentId.isBlank()) {

            return response(

                    400,

                    false,

                    "Invalid refund event."
            );
        }

        boolean matched = refundReconciliationService

                .reconcileFromWebhook(

                        refundId,

                        paymentId
                );

        if (!matched) {

            return response(

                    503,

                    false,

                    "Refund requires reconciliation."
            );
        }

        return response(

                200,

                true,

                "Refund webhook processed."
        );
    }

    // ==========================================
    // RESPONSE HELPER
    // ==========================================

    private ResponseEntity<Map<String, Object>> response(

            int status,

            boolean success,

            String message

    ) {

        return ResponseEntity.status(status)

                .body(Map.of(

                        "success", success,

                        "message", message
                ));
    }
}