package com.smartlibrary.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.smartlibrary.config.PaymentProperties;

import com.smartlibrary.dto.RazorpayDto.OrderResponse;
import com.smartlibrary.dto.RazorpayDto.PaymentResponse;

import com.smartlibrary.exception.RazorpayException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.MediaType;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import org.springframework.stereotype.Service;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import java.time.Duration;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RazorpayService {

    private static final Logger log =
            LoggerFactory.getLogger(RazorpayService.class);

    private static final String BASE_URL =
            "https://api.razorpay.com/v1";

    private static final String HMAC_ALGORITHM =
            "HmacSHA256";

    private final PaymentProperties properties;

    private final RestClient restClient;

    public RazorpayService(
            PaymentProperties properties
    ) {

        this.properties = properties;

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(
                Duration.ofSeconds(10)
        );

        factory.setReadTimeout(
                Duration.ofSeconds(30)
        );

        this.restClient = RestClient.builder()

                .baseUrl(BASE_URL)

                .requestFactory(factory)

                .defaultHeaders(headers -> {

                    headers.setBasicAuth(

                            properties.getRazorpayKeyId(),

                            properties.getRazorpayKeySecret()
                    );

                    headers.setContentType(
                            MediaType.APPLICATION_JSON
                    );

                    headers.setAccept(
                            List.of(MediaType.APPLICATION_JSON)
                    );
                })

                .build();
    }

    // ==========================================
    // ORDER PAYMENTS RESPONSE
    // ==========================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderPaymentsResponse(

            String entity,

            int count,

            List<PaymentResponse> items

    ) {
    }

    // ==========================================
    // CONFIGURATION VALIDATION
    // ==========================================

    public boolean isConfigured() {

        return properties.isOnlineEnabled()

                && hasText(
                properties.getRazorpayKeyId()
        )

                && hasText(
                properties.getRazorpayKeySecret()
        );
    }

    private void requireConfiguration() {

        if (!properties.isOnlineEnabled()) {

            throw new RazorpayException(
                    "Online payment is currently disabled."
            );
        }

        if (!isConfigured()) {

            throw new RazorpayException(
                    "Payment gateway is not configured."
            );
        }
    }

    // ==========================================
    // PUBLIC KEY FOR FRONTEND
    // ==========================================

    public String getPublicKeyId() {

        requireConfiguration();

        return properties.getRazorpayKeyId();
    }

    // ==========================================
    // CREATE RAZORPAY ORDER
    // ==========================================

    public OrderResponse createOrder(

            long amountPaise,

            String receipt,

            Long userId

    ) {

        requireConfiguration();

        if (amountPaise < 100) {

            throw new IllegalArgumentException(
                    "Minimum online payment amount is INR 1."
            );
        }

        if (!hasText(receipt)
                || receipt.length() > 40) {

            throw new IllegalArgumentException(
                    "Invalid order receipt reference."
            );
        }

        if (userId == null || userId <= 0) {

            throw new IllegalArgumentException(
                    "Invalid member ID."
            );
        }

        Map<String, Object> request =
                new LinkedHashMap<>();

        request.put(
                "amount",
                amountPaise
        );

        request.put(
                "currency",
                "INR"
        );

        request.put(
                "receipt",
                receipt
        );

        Map<String, String> notes =
                new LinkedHashMap<>();

        notes.put(
                "user_id",
                String.valueOf(userId)
        );

        notes.put(
                "purpose",
                "library_membership"
        );

        notes.put(
                "order_reference",
                receipt
        );

        request.put(
                "notes",
                notes
        );

        try {

            OrderResponse response =
                    restClient.post()

                            .uri("/orders")

                            .body(request)

                            .retrieve()

                            .body(OrderResponse.class);

            if (response == null
                    || !hasText(response.id())) {

                throw new RazorpayException(
                        "Payment gateway returned an invalid order."
                );
            }

            if (response.amount() != amountPaise

                    || !"INR".equals(response.currency())

                    || !receipt.equals(response.receipt())) {

                throw new RazorpayException(
                        "Payment order details do not match."
                );
            }

            log.info(
                    "Razorpay order created: {}",
                    response.id()
            );

            return response;

        } catch (RestClientResponseException ex) {

            throw handleGatewayError(
                    "create order",
                    ex
            );

        } catch (RestClientException ex) {

            log.error(
                    "Razorpay order request failed: {}",
                    ex.getClass().getSimpleName()
            );

            throw new RazorpayException(
                    "Unable to confirm payment order creation.",
                    ex
            );
        }
    }

    // ==========================================
    // FETCH RAZORPAY ORDER
    // ==========================================

    public OrderResponse fetchOrder(
            String razorpayOrderId
    ) {

        requireConfiguration();

        validateOrderId(razorpayOrderId);

        try {

            OrderResponse response =
                    restClient.get()

                            .uri(
                                    "/orders/{id}",
                                    razorpayOrderId
                            )

                            .retrieve()

                            .body(OrderResponse.class);

            if (response == null

                    || !razorpayOrderId.equals(
                    response.id()
            )) {

                throw new RazorpayException(
                        "Invalid payment order response."
                );
            }

            return response;

        } catch (RestClientResponseException ex) {

            throw handleGatewayError(
                    "fetch order",
                    ex
            );

        } catch (RestClientException ex) {

            throw new RazorpayException(
                    "Unable to fetch payment order.",
                    ex
            );
        }
    }

    // ==========================================
    // FETCH PAYMENT DETAILS
    // ==========================================

    public PaymentResponse fetchPayment(
            String razorpayPaymentId
    ) {

        requireConfiguration();

        validatePaymentId(razorpayPaymentId);

        try {

            PaymentResponse response =
                    restClient.get()

                            .uri(
                                    "/payments/{id}",
                                    razorpayPaymentId
                            )

                            .retrieve()

                            .body(PaymentResponse.class);

            if (response == null

                    || !razorpayPaymentId.equals(
                    response.id()
            )) {

                throw new RazorpayException(
                        "Invalid payment response."
                );
            }

            return response;

        } catch (RestClientResponseException ex) {

            throw handleGatewayError(
                    "fetch payment",
                    ex
            );

        } catch (RestClientException ex) {

            throw new RazorpayException(
                    "Unable to verify payment details.",
                    ex
            );
        }
    }

    // ==========================================
    // FETCH ALL PAYMENTS FOR AN ORDER
    // PHASE 6 - PAYMENT RECOVERY
    // ==========================================

    public List<PaymentResponse> fetchOrderPayments(
            String razorpayOrderId
    ) {

        requireConfiguration();

        validateOrderId(razorpayOrderId);

        try {

            OrderPaymentsResponse response =
                    restClient.get()

                            .uri(
                                    "/orders/{id}/payments",
                                    razorpayOrderId
                            )

                            .retrieve()

                            .body(
                                    OrderPaymentsResponse.class
                            );

            if (response == null
                    || response.items() == null) {

                throw new RazorpayException(
                        "Invalid order payments response."
                );
            }

            return response.items()

                    .stream()

                    .filter(payment ->
                            payment != null
                    )

                    .filter(payment ->
                            razorpayOrderId.equals(
                                    payment.orderId()
                            )
                    )

                    .toList();

        } catch (RestClientResponseException ex) {

            throw handleGatewayError(
                    "fetch order payments",
                    ex
            );

        } catch (RestClientException ex) {

            throw new RazorpayException(
                    "Unable to fetch order payments.",
                    ex
            );
        }
    }

    // ==========================================
    // CHECKOUT SIGNATURE VERIFICATION
    // ==========================================

    public boolean verifyPaymentSignature(

            String storedRazorpayOrderId,

            String razorpayPaymentId,

            String razorpaySignature

    ) {

        requireConfiguration();

        validateOrderId(
                storedRazorpayOrderId
        );

        validatePaymentId(
                razorpayPaymentId
        );

        if (!hasText(razorpaySignature)) {

            return false;
        }

        String payload =

                storedRazorpayOrderId

                        + "|"

                        + razorpayPaymentId;

        return verifyHmac(

                payload.getBytes(
                        StandardCharsets.UTF_8
                ),

                properties.getRazorpayKeySecret(),

                razorpaySignature
        );
    }

    // ==========================================
    // WEBHOOK SIGNATURE VERIFICATION
    // ==========================================

    public boolean verifyWebhookSignature(

            byte[] rawRequestBody,

            String webhookSignature

    ) {

        String webhookSecret =
                properties.getRazorpayWebhookSecret();

        if (!hasText(webhookSecret)) {

            log.warn(
                    "Razorpay webhook secret is not configured."
            );

            return false;
        }

        if (rawRequestBody == null

                || rawRequestBody.length == 0

                || !hasText(webhookSignature)) {

            return false;
        }

        return verifyHmac(

                rawRequestBody,

                webhookSecret,

                webhookSignature
        );
    }

    // ==========================================
    // VERIFY CAPTURED PAYMENT
    // ==========================================

    public PaymentResponse verifyCapturedPayment(

            String storedRazorpayOrderId,

            String razorpayPaymentId,

            long expectedAmountPaise

    ) {

        validateOrderId(
                storedRazorpayOrderId
        );

        if (expectedAmountPaise <= 0) {

            throw new IllegalArgumentException(
                    "Invalid expected payment amount."
            );
        }

        PaymentResponse payment =
                fetchPayment(razorpayPaymentId);

        // Verify order ownership.

        if (!storedRazorpayOrderId.equals(
                payment.orderId()
        )) {

            throw new RazorpayException(
                    "Payment does not belong to this order."
            );
        }

        // Verify exact amount.

        if (payment.amount()
                != expectedAmountPaise) {

            throw new RazorpayException(
                    "Payment amount does not match."
            );
        }

        // Verify currency.

        if (!"INR".equals(payment.currency())) {

            throw new RazorpayException(
                    "Invalid payment currency."
            );
        }

        // Verify captured status.

        if (!payment.isCaptured()) {

            throw new RazorpayException(
                    "Payment has not been captured yet."
            );
        }

        // Reject refunded payments.

        if (payment.amountRefunded() > 0) {

            throw new RazorpayException(
                    "This payment has already been refunded."
            );
        }

        return payment;
    }

    // ==========================================
    // HMAC SHA256 VERIFICATION
    // ==========================================

    private boolean verifyHmac(

            byte[] payload,

            String secret,

            String receivedSignature

    ) {

        if (!hasText(secret)

                || !hasText(receivedSignature)

                || !receivedSignature.matches(
                "[a-fA-F0-9]{64}"
        )) {

            return false;
        }

        try {

            Mac mac =
                    Mac.getInstance(HMAC_ALGORITHM);

            SecretKeySpec secretKey =
                    new SecretKeySpec(

                            secret.getBytes(
                                    StandardCharsets.UTF_8
                            ),

                            HMAC_ALGORITHM
                    );

            mac.init(secretKey);

            byte[] expectedSignature =
                    mac.doFinal(payload);

            byte[] actualSignature =
                    HexFormat.of().parseHex(
                            receivedSignature
                    );

            // Constant-time comparison.

            return MessageDigest.isEqual(

                    expectedSignature,

                    actualSignature
            );

        } catch (GeneralSecurityException ex) {

            throw new RazorpayException(
                    "Unable to verify payment signature.",
                    ex
            );

        } catch (IllegalArgumentException ex) {

            return false;
        }
    }

    // ==========================================
    // VALIDATION HELPERS
    // ==========================================

    private void validateOrderId(
            String orderId
    ) {

        if (!hasText(orderId)

                || !orderId.matches(
                "order_[A-Za-z0-9]+"
        )

                || orderId.length() > 100) {

            throw new IllegalArgumentException(
                    "Invalid Razorpay order ID."
            );
        }
    }

    private void validatePaymentId(
            String paymentId
    ) {

        if (!hasText(paymentId)

                || !paymentId.matches(
                "pay_[A-Za-z0-9]+"
        )

                || paymentId.length() > 100) {

            throw new IllegalArgumentException(
                    "Invalid Razorpay payment ID."
            );
        }
    }

    private boolean hasText(
            String value
    ) {

        return value != null
                && !value.isBlank();
    }

    // ==========================================
    // GATEWAY ERROR HANDLING
    // ==========================================

    private RazorpayException handleGatewayError(

            String operation,

            RestClientResponseException ex

    ) {

        log.warn(

                "Razorpay {} failed. HTTP status: {}",

                operation,

                ex.getStatusCode().value()
        );

        return new RazorpayException(

                "Payment gateway request failed. "
                        + "Please try again later.",

                ex
        );
    }
}