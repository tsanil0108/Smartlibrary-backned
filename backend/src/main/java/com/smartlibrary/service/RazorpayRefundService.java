package com.smartlibrary.service;

import com.smartlibrary.config.PaymentProperties;
import com.smartlibrary.dto.RazorpayRefundDto.RefundResponse;
import com.smartlibrary.dto.RazorpayRefundDto.RefundCollection;
import com.smartlibrary.exception.RazorpayException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class RazorpayRefundService {

    private static final String BASE_URL =
            "https://api.razorpay.com/v1";

    private final PaymentProperties properties;

    private final RestClient restClient;

    public RazorpayRefundService(
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
    // CONFIGURATION
    // ==========================================

    public boolean isConfigured() {

        return hasText(
                properties.getRazorpayKeyId()
        )

                && hasText(
                properties.getRazorpayKeySecret()
        );
    }

    private void requireConfiguration() {

        if (!isConfigured()) {

            throw new RazorpayException(
                    "Razorpay refund gateway is not configured."
            );
        }
    }

    // ==========================================
    // CREATE FULL REFUND
    // ==========================================

    public RefundResponse createRefund(

            String paymentId,

            long amountPaise,

            String receipt,

            String reason

    ) {

        requireConfiguration();

        validatePaymentId(paymentId);

        if (amountPaise <= 0) {

            throw new IllegalArgumentException(
                    "Refund amount must be greater than zero."
            );
        }

        if (!hasText(receipt)
                || receipt.length() > 40) {

            throw new IllegalArgumentException(
                    "Invalid refund reference."
            );
        }

        Map<String, Object> request =
                new LinkedHashMap<>();

        request.put(
                "amount",
                amountPaise
        );

        request.put(
                "speed",
                "normal"
        );

        request.put(
                "receipt",
                receipt
        );

        Map<String, String> notes =
                new LinkedHashMap<>();

        notes.put(
                "purpose",
                "library_membership_refund"
        );

        notes.put(
                "refund_reference",
                receipt
        );

        String safeReason =
                reason == null ? "" : reason;

        if (safeReason.length() > 250) {

            safeReason =
                    safeReason.substring(0, 250);
        }

        notes.put(
                "reason",
                safeReason
        );

        request.put(
                "notes",
                notes
        );

        try {

            RefundResponse response =
                    restClient.post()

                            .uri(
                                    "/payments/{id}/refund",
                                    paymentId
                            )

                            .body(request)

                            .retrieve()

                            .body(RefundResponse.class);

            validateResponse(

                    response,

                    paymentId,

                    amountPaise,

                    receipt
            );

            log.info(

                    "Razorpay refund submitted: {}",

                    response.id()
            );

            return response;

        } catch (RestClientResponseException ex) {

            throw gatewayError(
                    "create refund",
                    ex
            );

        } catch (RestClientException ex) {

            throw new RazorpayException(

                    "Refund submission could not be confirmed.",

                    ex
            );
        }
    }

    // ==========================================
    // FETCH SINGLE REFUND
    // ==========================================

    public RefundResponse fetchRefund(
            String refundId
    ) {

        requireConfiguration();

        validateRefundId(refundId);

        try {

            RefundResponse response =
                    restClient.get()

                            .uri(
                                    "/refunds/{id}",
                                    refundId
                            )

                            .retrieve()

                            .body(RefundResponse.class);

            if (response == null

                    || !refundId.equals(response.id())) {

                throw new RazorpayException(
                        "Invalid refund response."
                );
            }

            return response;

        } catch (RestClientResponseException ex) {

            throw gatewayError(
                    "fetch refund",
                    ex
            );

        } catch (RestClientException ex) {

            throw new RazorpayException(

                    "Unable to fetch refund details.",

                    ex
            );
        }
    }

    // ==========================================
    // FETCH ALL REFUNDS FOR PAYMENT
    // ==========================================

    public List<RefundResponse> fetchPaymentRefunds(
            String paymentId
    ) {

        requireConfiguration();

        validatePaymentId(paymentId);

        List<RefundResponse> refunds =
                new ArrayList<>();

        final int pageSize = 100;

        try {

            for (int skip = 0; skip < 10000;
                 skip += pageSize) {

                // FIX: Lambda requires a final
                // or effectively final variable.

                final int currentSkip = skip;

                RefundCollection response =
                        restClient.get()

                                .uri(builder -> builder

                                        .path(
                                                "/payments/{id}/refunds"
                                        )

                                        .queryParam(
                                                "count",
                                                pageSize
                                        )

                                        .queryParam(
                                                "skip",
                                                currentSkip
                                        )

                                        .build(paymentId)
                                )

                                .retrieve()

                                .body(RefundCollection.class);

                if (response == null
                        || response.items() == null) {

                    throw new RazorpayException(
                            "Invalid refund collection."
                    );
                }

                for (RefundResponse refund
                        : response.items()) {

                    if (refund == null

                            || !paymentId.equals(
                            refund.paymentId()
                    )) {

                        throw new RazorpayException(
                                "Unexpected refund payment reference."
                        );
                    }

                    refunds.add(refund);
                }

                if (response.items().size() < pageSize) {

                    return List.copyOf(refunds);
                }
            }

            throw new RazorpayException(
                    "Refund collection limit exceeded."
            );

        } catch (RestClientResponseException ex) {

            throw gatewayError(
                    "fetch payment refunds",
                    ex
            );

        } catch (RestClientException ex) {

            throw new RazorpayException(

                    "Unable to fetch payment refunds.",

                    ex
            );
        }
    }

    // ==========================================
    // RESPONSE VALIDATION
    // ==========================================

    public void validateResponse(

            RefundResponse response,

            String paymentId,

            long amountPaise,

            String receipt

    ) {

        if (response == null) {

            throw new RazorpayException(
                    "Empty refund response."
            );
        }

        validateRefundId(response.id());

        if (!paymentId.equals(response.paymentId())

                || response.amount() != amountPaise

                || !"INR".equals(response.currency())

                || !receipt.equals(response.receipt())) {

            throw new RazorpayException(
                    "Refund response details do not match."
            );
        }

        if (!response.isPending()

                && !response.isProcessed()

                && !response.isFailed()) {

            throw new RazorpayException(
                    "Unknown refund status."
            );
        }
    }

    // ==========================================
    // PAYMENT ID VALIDATION
    // ==========================================

    private void validatePaymentId(String paymentId) {

        if (!hasText(paymentId)

                || paymentId.length() > 100

                || !paymentId.matches("pay_[A-Za-z0-9]+")) {

            throw new IllegalArgumentException(
                    "Invalid Razorpay payment ID."
            );
        }
    }

    // ==========================================
    // REFUND ID VALIDATION
    // ==========================================

    private void validateRefundId(String refundId) {

        if (!hasText(refundId)

                || refundId.length() > 100

                || !refundId.matches("rfnd_[A-Za-z0-9]+")) {

            throw new IllegalArgumentException(
                    "Invalid Razorpay refund ID."
            );
        }
    }

    // ==========================================
    // TEXT VALIDATION
    // ==========================================

    private boolean hasText(String value) {

        return value != null && !value.isBlank();
    }

    // ==========================================
    // GATEWAY ERROR HANDLING
    // ==========================================

    private RazorpayException gatewayError(

            String operation,

            RestClientResponseException ex

    ) {

        log.warn(

                "Razorpay {} failed. HTTP status: {}",

                operation,

                ex.getStatusCode().value()
        );

        return new RazorpayException(

                "Refund gateway request could not be confirmed.",

                ex
        );
    }
}