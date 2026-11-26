package com.smartlibrary.service;

import com.smartlibrary.dto.AdminPaymentDto.RefundRequest;
import com.smartlibrary.dto.AdminPaymentDto.RefundResponse;

import com.smartlibrary.dto.RazorpayDto.PaymentResponse;

import com.smartlibrary.entity.DemoPayment;
import com.smartlibrary.entity.PaymentOrder;
import com.smartlibrary.entity.PaymentOrderStatus;
import com.smartlibrary.entity.PaymentRefund;
import com.smartlibrary.entity.RefundStatus;

import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;
import com.smartlibrary.entity.UserStatus;

import com.smartlibrary.exception.BusinessRuleException;

import com.smartlibrary.repository.DemoPaymentRepository;
import com.smartlibrary.repository.PaymentOrderRepository;
import com.smartlibrary.repository.PaymentRefundRepository;
import com.smartlibrary.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class PaymentRefundService {

    private final PaymentRefundRepository refunds;

    private final DemoPaymentRepository payments;

    private final PaymentOrderRepository orders;

    private final UserRepository users;

    private final RazorpayService razorpayService;

    private final RazorpayRefundService refundGateway;

    private final TransactionTemplate transaction;

    private final Clock clock;

    public PaymentRefundService(

            PaymentRefundRepository refunds,

            DemoPaymentRepository payments,

            PaymentOrderRepository orders,

            UserRepository users,

            RazorpayService razorpayService,

            RazorpayRefundService refundGateway,

            PlatformTransactionManager transactionManager,

            Clock clock

    ) {

        this.refunds = refunds;

        this.payments = payments;

        this.orders = orders;

        this.users = users;

        this.razorpayService = razorpayService;

        this.refundGateway = refundGateway;

        this.clock = clock;

        this.transaction =
                new TransactionTemplate(transactionManager);

        this.transaction.setPropagationBehavior(

                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    private record PreparedRefund(

            Long refundId,

            boolean newlyCreated

    ) {
    }

    // ==========================================
    // ADMIN REFUND REQUEST
    // ==========================================

    public RefundResponse requestRefund(

            Long paymentId,

            Long adminId,

            RefundRequest request

    ) {

        validateAdmin(adminId);

        String reason = normalizeReason(
                request.reason()
        );

        PaymentRefund existing = refunds

                .findByPaymentId(paymentId)

                .orElse(null);

        if (existing != null) {

            return RefundResponse.from(existing);
        }

        if (!refundGateway.isConfigured()

                || !razorpayService.isConfigured()) {

            throw error(

                    "REFUND_GATEWAY_DISABLED",

                    "Razorpay payment gateway is not configured."
            );
        }

        // Verify gateway payment before creating refund.

        DemoPayment snapshot = getPayment(paymentId);

        validateEligiblePayment(snapshot);

        PaymentResponse gatewayPayment =

                razorpayService.fetchPayment(

                        snapshot.getRazorpayPaymentId()
                );

        validateGatewayPayment(

                snapshot,

                gatewayPayment
        );

        // Save refund request before external submission.

        PreparedRefund prepared = Objects.requireNonNull(

                transaction.execute(status ->

                        prepareRefund(

                                paymentId,

                                adminId,

                                reason,

                                gatewayPayment
                        )
                )
        );

        // Existing request must never submit another refund.

        if (!prepared.newlyCreated()) {

            return getRefund(prepared.refundId());
        }

        PaymentRefund refund = findRefund(
                prepared.refundId()
        );

        try {

            // Recheck gateway before submitting.

            DemoPayment payment = getPayment(paymentId);

            PaymentResponse latestPayment =

                    razorpayService.fetchPayment(

                            refund.getRazorpayPaymentId()
                    );

            validateGatewayPayment(

                    payment,

                    latestPayment
            );

            com.smartlibrary.dto.RazorpayRefundDto.RefundResponse

                    gatewayResponse = refundGateway.createRefund(

                    refund.getRazorpayPaymentId(),

                    refund.getAmountPaise(),

                    refund.getRefundReference(),

                    refund.getReason()
            );

            return applyGatewayResponse(

                    refund.getId(),

                    gatewayResponse
            );

        } catch (RuntimeException ex) {

            log.warn(

                    "Refund {} requires reconciliation: {}",

                    refund.getId(),

                    ex.getClass().getSimpleName()
            );

            return markReconciliationRequired(

                    refund.getId(),

                    "Refund outcome could not be confirmed. "
                            + "Do not submit another refund."
            );
        }
    }

    // ==========================================
    // PREPARE REFUND
    // DATABASE TRANSACTION
    // ==========================================

    private PreparedRefund prepareRefund(

            Long paymentId,

            Long adminId,

            String reason,

            PaymentResponse gatewayPayment

    ) {

        DemoPayment snapshot = getPayment(paymentId);

        if (snapshot.getPaymentOrderId() == null) {

            throw error(

                    "PAYMENT_ORDER_MISSING",

                    "Payment order reference is missing."
            );
        }

        PaymentOrder order = orders

                .findByIdForUpdate(
                        snapshot.getPaymentOrderId()
                )

                .orElseThrow(() -> error(

                        "ORDER_NOT_FOUND",

                        "Payment order not found."
                ));

        PaymentRefund existing = refunds

                .findByPaymentId(paymentId)

                .orElse(null);

        if (existing != null) {

            return new PreparedRefund(

                    existing.getId(),

                    false
            );
        }

        // Reload payment state after acquiring order lock.

        DemoPayment payment = getPayment(paymentId);

        validateEligiblePayment(payment);

        validateGatewayPayment(

                payment,

                gatewayPayment
        );

        if (order.getStatus()
                != PaymentOrderStatus.SUCCESS) {

            throw error(

                    "INVALID_ORDER_STATUS",

                    "Only successful orders can be refunded."
            );
        }

        validateOrderLink(

                payment,

                order
        );

        PaymentRefund refund = new PaymentRefund();

        refund.setPaymentId(payment.getId());

        refund.setPaymentOrderId(order.getId());

        refund.setUserId(payment.getUserId());

        refund.setRequestedBy(adminId);

        refund.setRefundReference(

                "RFD-" + UUID.randomUUID()

                        .toString()

                        .replace("-", "")
        );

        refund.setRazorpayPaymentId(

                payment.getRazorpayPaymentId()
        );

        refund.setAmountPaise(

                payment.getPaidPaise()
        );

        refund.setCurrency("INR");

        refund.setReason(reason);

        refund.setStatus(
                RefundStatus.REQUESTED
        );

        refund.setCreatedAt(
                Instant.now(clock)
        );

        refunds.saveAndFlush(refund);

        payment.setStoredStatus(
                "REFUND_PENDING"
        );

        order.setStatus(
                PaymentOrderStatus.REFUND_PENDING
        );

        payments.save(payment);

        orders.save(order);

        log.info(

                "Refund requested. Refund ID: {}, "
                        + "payment ID: {}, admin ID: {}",

                refund.getId(),

                paymentId,

                adminId
        );

        return new PreparedRefund(

                refund.getId(),

                true
        );
    }

    // ==========================================
    // APPLY VERIFIED GATEWAY RESPONSE
    // ==========================================

    public RefundResponse applyGatewayResponse(

            Long refundId,

            com.smartlibrary.dto.RazorpayRefundDto.RefundResponse
                    gatewayResponse

    ) {

        return Objects.requireNonNull(

                transaction.execute(status -> {

                    PaymentRefund snapshot =
                            findRefund(refundId);

                    // Consistent lock order:
                    // PaymentOrder -> PaymentRefund

                    PaymentOrder order = orders

                            .findByIdForUpdate(

                                    snapshot.getPaymentOrderId()
                            )

                            .orElseThrow(() -> error(

                                    "ORDER_NOT_FOUND",

                                    "Payment order not found."
                            ));

                    PaymentRefund refund = refunds

                            .findByIdForUpdate(refundId)

                            .orElseThrow(() -> error(

                                    "REFUND_NOT_FOUND",

                                    "Refund not found."
                            ));

                    refundGateway.validateResponse(

                            gatewayResponse,

                            refund.getRazorpayPaymentId(),

                            refund.getAmountPaise(),

                            refund.getRefundReference()
                    );

                    if (refund.getRazorpayRefundId() != null

                            && !refund.getRazorpayRefundId()

                            .equals(gatewayResponse.id())) {

                        throw error(

                                "REFUND_ID_MISMATCH",

                                "Refund gateway ID does not match."
                        );
                    }

                    if (refund.getStatus()
                            == RefundStatus.PROCESSED) {

                        return RefundResponse.from(refund);
                    }

                    DemoPayment payment = getPayment(

                            refund.getPaymentId()
                    );

                    validateOrderLink(payment, order);

                    if (refund.getAmountPaise()
                            != payment.getPaidPaise()

                            || !Objects.equals(

                            refund.getRazorpayPaymentId(),

                            payment.getRazorpayPaymentId()

                    )) {

                        throw error(

                                "REFUND_PAYMENT_MISMATCH",

                                "Refund payment details do not match."
                        );
                    }

                    refund.setRazorpayRefundId(

                            gatewayResponse.id()
                    );

                    refund.setLastCheckedAt(

                            Instant.now(clock)
                    );

                    if (gatewayResponse.isProcessed()) {

                        refund.setStatus(
                                RefundStatus.PROCESSED
                        );

                        refund.setProcessedAt(
                                Instant.now(clock)
                        );

                        refund.setFailureReason(null);

                        payment.setStoredStatus(
                                "REFUNDED"
                        );

                        order.setStatus(
                                PaymentOrderStatus.REFUNDED
                        );

                    } else if (gatewayResponse.isPending()) {

                        refund.setStatus(
                                RefundStatus.PROCESSING
                        );

                        refund.setFailureReason(null);

                        payment.setStoredStatus(
                                "REFUND_PENDING"
                        );

                        order.setStatus(
                                PaymentOrderStatus.REFUND_PENDING
                        );

                    } else {

                        refund.setStatus(
                                RefundStatus.FAILED
                        );

                        refund.setFailureReason(

                                "Gateway reported refund failure. "
                                        + "Manual reconciliation required."
                        );

                        // Keep payment blocked from new refund
                        // until gateway state is reconciled.

                        payment.setStoredStatus(
                                "REFUND_PENDING"
                        );

                        order.setStatus(
                                PaymentOrderStatus.REFUND_PENDING
                        );
                    }

                    payments.save(payment);

                    orders.save(order);

                    refunds.saveAndFlush(refund);

                    log.info(

                            "Refund {} updated to {}",

                            refund.getId(),

                            refund.getStatus()
                    );

                    return RefundResponse.from(refund);
                })
        );
    }

    // ==========================================
    // UNKNOWN REFUND OUTCOME
    // ==========================================

    private RefundResponse markReconciliationRequired(

            Long refundId,

            String reason

    ) {

        return Objects.requireNonNull(

                transaction.execute(status -> {

                    PaymentRefund snapshot =
                            findRefund(refundId);

                    orders.findByIdForUpdate(

                            snapshot.getPaymentOrderId()

                    ).orElseThrow(() -> error(

                            "ORDER_NOT_FOUND",

                            "Payment order not found."
                    ));

                    PaymentRefund refund = refunds

                            .findByIdForUpdate(refundId)

                            .orElseThrow(() -> error(

                                    "REFUND_NOT_FOUND",

                                    "Refund not found."
                            ));

                    if (refund.getStatus()
                            == RefundStatus.PROCESSED) {

                        return RefundResponse.from(refund);
                    }

                    refund.setStatus(

                            RefundStatus.RECONCILIATION_REQUIRED
                    );

                    refund.setFailureReason(reason);

                    refund.setLastCheckedAt(

                            Instant.now(clock)
                    );

                    refunds.saveAndFlush(refund);

                    return RefundResponse.from(refund);
                })
        );
    }

    // ==========================================
    // GET REFUND
    // ==========================================

    public RefundResponse getRefund(Long refundId) {

        return RefundResponse.from(

                findRefund(refundId)
        );
    }

    public RefundResponse getRefundByPayment(
            Long paymentId
    ) {

        return refunds.findByPaymentId(paymentId)

                .map(RefundResponse::from)

                .orElseThrow(() -> error(

                        "REFUND_NOT_FOUND",

                        "No refund exists for this payment."
                ));
    }

    private PaymentRefund findRefund(Long refundId) {

        return refunds.findById(refundId)

                .orElseThrow(() -> error(

                        "REFUND_NOT_FOUND",

                        "Refund not found."
                ));
    }

    private DemoPayment getPayment(Long paymentId) {

        return payments.findById(paymentId)

                .orElseThrow(() -> error(

                        "PAYMENT_NOT_FOUND",

                        "Payment not found."
                ));
    }

    // ==========================================
    // VALIDATE ADMIN
    // ==========================================

    private void validateAdmin(Long adminId) {

        User admin = users.findById(adminId)

                .orElseThrow(() -> error(

                        "ADMIN_NOT_FOUND",

                        "Admin account not found."
                ));

        if (admin.getRole() != Role.ADMIN

                || admin.getStatus() != UserStatus.ACTIVE) {

            throw error(

                    "REFUND_ACCESS_DENIED",

                    "Only active administrators can request refunds."
            );
        }
    }

    // ==========================================
    // VALIDATE PAYMENT ELIGIBILITY
    // ==========================================

    private void validateEligiblePayment(
            DemoPayment payment
    ) {

        if (payment.getPaidPaise() <= 0) {

            throw error(

                    "FREE_PAYMENT_NOT_REFUNDABLE",

                    "Coupon-only payments cannot be refunded."
            );
        }

        if (!payment.isVerifiedOnlinePayment()) {

            throw error(

                    "PAYMENT_NOT_REFUNDABLE",

                    "Only verified successful online payments "
                            + "can be refunded."
            );
        }

        if (payment.getPaymentOrderId() == null

                || payment.getRazorpayOrderId() == null

                || !"INR".equals(payment.getCurrency())) {

            throw error(

                    "INVALID_PAYMENT_DETAILS",

                    "Required payment details are missing."
            );
        }
    }

    // ==========================================
    // VALIDATE ORDER LINK
    // ==========================================

    private void validateOrderLink(

            DemoPayment payment,

            PaymentOrder order

    ) {

        if (!Objects.equals(

                payment.getPaymentOrderId(),

                order.getId()

        )

                || !Objects.equals(

                payment.getId(),

                order.getCompletedPaymentId()

        )

                || !Objects.equals(

                payment.getUserId(),

                order.getUserId()

        )

                || !Objects.equals(

                payment.getRazorpayPaymentId(),

                order.getRazorpayPaymentId()

        )

                || !Objects.equals(

                payment.getRazorpayOrderId(),

                order.getRazorpayOrderId()

        )

                || !Objects.equals(

                payment.getMembershipId(),

                order.getMembershipId()

        )

                || payment.getPaidPaise()
                != order.getPayablePaise()

                || !"INR".equals(order.getCurrency())) {

            throw error(

                    "PAYMENT_ORDER_MISMATCH",

                    "Payment and order details do not match."
            );
        }
    }

    // ==========================================
    // VERIFY GATEWAY PAYMENT
    // ==========================================

    private void validateGatewayPayment(

            DemoPayment payment,

            PaymentResponse gatewayPayment

    ) {

        if (!gatewayPayment.isCaptured()

                || gatewayPayment.amount()
                != payment.getPaidPaise()

                || !"INR".equals(gatewayPayment.currency())

                || !Objects.equals(

                payment.getRazorpayPaymentId(),

                gatewayPayment.id()

        )

                || !Objects.equals(

                payment.getRazorpayOrderId(),

                gatewayPayment.orderId()

        )) {

            throw error(

                    "GATEWAY_PAYMENT_MISMATCH",

                    "Gateway payment verification failed."
            );
        }

        if (gatewayPayment.amountRefunded() > 0) {

            throw error(

                    "PAYMENT_ALREADY_REFUNDED",

                    "Payment already has a gateway refund. "
                            + "Manual reconciliation is required."
            );
        }
    }

    // ==========================================
    // VALIDATE REASON
    // ==========================================

    private String normalizeReason(String reason) {

        if (reason == null) {

            throw error(

                    "REFUND_REASON_REQUIRED",

                    "Refund reason is required."
            );
        }

        String normalized = reason.trim();

        if (normalized.length() < 5

                || normalized.length() > 500) {

            throw error(

                    "INVALID_REFUND_REASON",

                    "Refund reason must contain "
                            + "5 to 500 characters."
            );
        }

        return normalized;
    }

    private BusinessRuleException error(

            String code,

            String message

    ) {

        return new BusinessRuleException(

                code,

                message
        );
    }
}