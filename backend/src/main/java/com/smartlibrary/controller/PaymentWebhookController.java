package com.smartlibrary.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartlibrary.repository.PaymentOrderRepository;

import com.smartlibrary.service.PaymentCompletionService;
import com.smartlibrary.service.RazorpayService;

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

        // Verify raw request body before JSON parsing.

        if (!razorpayService.verifyWebhookSignature(
                rawBody,
                signature
        )) {

            return ResponseEntity.status(401).body(

                    Map.of(

                            "success", false,

                            "message", "Invalid webhook signature."
                    )
            );
        }

        try {

            JsonNode root =
                    objectMapper.readTree(rawBody);

            String event = root
                    .path("event")
                    .asText("");

            if ("payment.captured".equals(event)) {

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

                    return ResponseEntity.badRequest()
                            .body(Map.of(

                                    "success", false,

                                    "message", "Invalid payment event."
                            ));
                }

                // Ignore unrelated orders.

                if (orders.findByRazorpayOrderId(
                        orderId
                ).isEmpty()) {

                    log.warn(
                            "Webhook received for unknown order: {}",
                            orderId
                    );

                    // Return retryable response because
                    // the order transaction may not be committed.

                    return ResponseEntity.status(503)
                            .body(Map.of(

                                    "success", false,

                                    "message", "Order not available."
                            ));
                }

                completionService.completeFromWebhook(

                        orderId,

                        paymentId
                );

                log.info(
                        "Payment webhook processed: {}",
                        paymentId
                );
            }

            // Other event types do not activate membership.

            return ResponseEntity.ok(

                    Map.of(

                            "success", true,

                            "message", "Webhook received."
                    )
            );

        } catch (IOException ex) {

            return ResponseEntity.badRequest()
                    .body(Map.of(

                            "success", false,

                            "message", "Invalid webhook payload."
                    ));

        } catch (RuntimeException ex) {

            log.error(

                    "Payment webhook processing failed: {}",

                    ex.getClass().getSimpleName()
            );

            // Non-2xx response allows gateway retry.

            return ResponseEntity.status(500)
                    .body(Map.of(

                            "success", false,

                            "message", "Webhook processing failed."
                    ));
        }
    }
}