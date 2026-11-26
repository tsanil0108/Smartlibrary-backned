package com.smartlibrary.service;

import com.smartlibrary.dto.AdminPaymentDto.RefundResponse;
import com.smartlibrary.entity.PaymentRefund;
import com.smartlibrary.entity.RefundStatus;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.RazorpayException;
import com.smartlibrary.repository.PaymentRefundRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundReconciliationService {

    private final PaymentRefundRepository refunds;

    private final RazorpayRefundService refundGateway;

    private final PaymentRefundService refundService;

    // ==========================================
    // MANUAL / AUTOMATIC RECONCILIATION
    // ==========================================

    public RefundResponse reconcile(Long refundId) {

        PaymentRefund refund = refunds.findById(refundId)

                .orElseThrow(() ->
                        new BusinessRuleException(
                                "REFUND_NOT_FOUND",
                                "Refund record not found."
                        )
                );

        if (refund.getStatus() == RefundStatus.PROCESSED) {

            return RefundResponse.from(refund);
        }

        if (!refundGateway.isConfigured()) {

            throw new BusinessRuleException(
                    "REFUND_GATEWAY_DISABLED",
                    "Razorpay refund gateway is not configured."
            );
        }

        // Refund ID already available.

        if (hasText(refund.getRazorpayRefundId())) {

            var gatewayRefund = refundGateway.fetchRefund(

                    refund.getRazorpayRefundId()
            );

            return refundService.applyGatewayResponse(

                    refund.getId(),

                    gatewayRefund
            );
        }

        // Refund ID missing.
        // Recover using the original payment and receipt.

        var gatewayRefunds = refundGateway.fetchPaymentRefunds(

                refund.getRazorpayPaymentId()
        );

        var matches = gatewayRefunds.stream()

                .filter(Objects::nonNull)

                .filter(item ->
                        refund.getRefundReference()
                                .equals(item.receipt())
                )

                .toList();

        if (matches.size() > 1) {

            throw new RazorpayException(
                    "Multiple matching refunds found. "
                            + "Manual investigation required."
            );
        }

        if (matches.isEmpty()) {

            log.warn(
                    "No matching gateway refund found for local refund {}",
                    refundId
            );

            // Never create another refund automatically.
            // The original submission may still be processing.

            return refundService.getRefund(refundId);
        }

        return refundService.applyGatewayResponse(

                refund.getId(),

                matches.get(0)
        );
    }

    // ==========================================
    // REFUND WEBHOOK RECONCILIATION
    // ==========================================

    public boolean reconcileFromWebhook(

            String razorpayRefundId,

            String razorpayPaymentId

    ) {

        if (!hasText(razorpayRefundId)

                || !hasText(razorpayPaymentId)) {

            throw new IllegalArgumentException(
                    "Refund webhook identifiers are missing."
            );
        }

        // Fetch authoritative gateway state.
        // Do not directly trust webhook status.

        var gatewayRefund = refundGateway.fetchRefund(

                razorpayRefundId
        );

        if (!razorpayPaymentId.equals(
                gatewayRefund.paymentId()
        )) {

            throw new RazorpayException(
                    "Webhook refund payment mismatch."
            );
        }

        PaymentRefund localRefund = refunds

                .findByRazorpayRefundId(razorpayRefundId)

                .orElse(null);

        // Recover if application crashed before saving refund ID.

        if (localRefund == null

                && hasText(gatewayRefund.receipt())) {

            localRefund = refunds

                    .findByRefundReference(
                            gatewayRefund.receipt()
                    )

                    .orElse(null);
        }

        if (localRefund == null) {

            log.warn(
                    "Unmatched gateway refund: {}, payment: {}",
                    razorpayRefundId,
                    razorpayPaymentId
            );

            // External refunds require manual investigation.

            return false;
        }

        if (!razorpayPaymentId.equals(
                localRefund.getRazorpayPaymentId()
        )) {

            throw new RazorpayException(
                    "Local refund payment mismatch."
            );
        }

        refundService.applyGatewayResponse(

                localRefund.getId(),

                gatewayRefund
        );

        log.info(
                "Refund webhook reconciled: {}",
                razorpayRefundId
        );

        return true;
    }

    private boolean hasText(String value) {

        return value != null && !value.isBlank();
    }
}