package com.smartlibrary.service;

import com.smartlibrary.dto.AdminPaymentDto.PaymentSummary;
import com.smartlibrary.dto.AdminPaymentDto.RefundResponse;
import com.smartlibrary.dto.AdminPaymentDto.ReconciliationResponse;

import com.smartlibrary.dto.RazorpayDto.PaymentResponse;

import com.smartlibrary.entity.DemoPayment;
import com.smartlibrary.entity.PaymentOrder;
import com.smartlibrary.entity.PaymentOrderStatus;
import com.smartlibrary.entity.RefundStatus;

import com.smartlibrary.repository.DemoPaymentRepository;
import com.smartlibrary.repository.PaymentOrderRepository;
import com.smartlibrary.repository.PaymentRefundRepository;

import com.smartlibrary.exception.BusinessRuleException;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminPaymentService {

    private final DemoPaymentRepository payments;

    private final PaymentOrderRepository orders;

    private final PaymentRefundRepository refunds;

    private final RazorpayService razorpayService;

    private final PaymentCompletionService completionService;

    // ==========================================
    // ALL PAYMENT TRANSACTIONS
    // ==========================================

    @Transactional(readOnly = true)
    public List<DemoPayment> getAllPayments() {

        return payments.findAll(

                Sort.by(
                        Sort.Direction.DESC,
                        "id"
                )
        );
    }

    // ==========================================
    // PAYMENT STATISTICS
    // ==========================================

    @Transactional(readOnly = true)
    public PaymentSummary getSummary() {

        List<DemoPayment> list = payments.findAll();

        long successfulPayments = 0;

        long onlinePayments = 0;

        long couponRedemptions = 0;

        long grossRevenue = 0;

        long totalDiscount = 0;

        for (DemoPayment payment : list) {

            String status = payment.getStatus();

            boolean successful =
                    payment.isSuccessfulPayment();

            boolean previouslyVerifiedOnline =

                    payment.getPaidPaise() > 0

                            && payment.getVerifiedAt() != null

                            && payment.getMembershipId() != null

                            && payment.getRazorpayPaymentId() != null

                            && (
                            "REFUNDED".equals(status)
                                    || "REFUND_PENDING".equals(status)
                    );

            if (successful) {

                successfulPayments++;
            }

            if (successful && payment.isFreeRedemption()) {

                couponRedemptions++;
            }

            if (payment.isVerifiedOnlinePayment()
                    || previouslyVerifiedOnline) {

                onlinePayments++;

                grossRevenue += payment.getPaidPaise();
            }

            if (successful || previouslyVerifiedOnline) {

                totalDiscount += payment.getDiscountPaise();
            }
        }

        long pendingOrders =

                orders.countByStatus(
                        PaymentOrderStatus.CREATED
                )

                        + orders.countByStatus(
                        PaymentOrderStatus.PENDING
                )

                        + orders.countByStatus(
                        PaymentOrderStatus.CAPTURED
                );

        long refundPending =

                refunds.countByStatus(
                        RefundStatus.REQUESTED
                )

                        + refunds.countByStatus(
                        RefundStatus.PROCESSING
                )

                        + refunds.countByStatus(
                        RefundStatus.RECONCILIATION_REQUIRED
                );

        long refundedPayments =
                refunds.countByStatus(
                        RefundStatus.PROCESSED
                );

        long totalRefunded =
                refunds.sumAmountByStatus(
                        RefundStatus.PROCESSED
                );

        return new PaymentSummary(

                list.size(),

                successfulPayments,

                onlinePayments,

                couponRedemptions,

                pendingOrders,

                refundPending,

                refundedPayments,

                grossRevenue,

                totalDiscount,

                totalRefunded,

                grossRevenue - totalRefunded
        );
    }

    // ==========================================
    // PAYMENT ORDERS
    // ==========================================

    @Transactional(readOnly = true)
    public Page<PaymentOrder> getOrders(

            PaymentOrderStatus status,

            Pageable pageable

    ) {

        if (status == null) {

            return orders.findAllByOrderByIdDesc(
                    pageable
            );
        }

        return orders.findByStatusOrderByIdDesc(

                status,

                pageable
        );
    }

    // ==========================================
    // SINGLE PAYMENT ORDER
    // ==========================================

    @Transactional(readOnly = true)
    public PaymentOrder getOrder(Long orderId) {

        return orders.findById(orderId)

                .orElseThrow(() ->
                        new BusinessRuleException(

                                "ORDER_NOT_FOUND",

                                "Payment order not found."
                        )
                );
    }

    // ==========================================
    // REFUND HISTORY
    // ==========================================

    @Transactional(readOnly = true)
    public Page<RefundResponse> getRefunds(

            RefundStatus status,

            Pageable pageable

    ) {

        if (status == null) {

            return refunds

                    .findAllByOrderByIdDesc(pageable)

                    .map(RefundResponse::from);
        }

        return refunds

                .findByStatusOrderByIdDesc(

                        status,

                        pageable
                )

                .map(RefundResponse::from);
    }

    // ==========================================
    // MANUAL PAYMENT RECONCILIATION
    // ==========================================

    public ReconciliationResponse reconcile(
            Long orderId
    ) {

        PaymentOrder order = getOrder(orderId);

        if (order.getStatus() == PaymentOrderStatus.SUCCESS) {

            return response(

                    order,

                    "Payment is already completed."
            );
        }

        if (order.getStatus() != PaymentOrderStatus.PENDING

                && order.getStatus() != PaymentOrderStatus.CAPTURED) {

            throw new BusinessRuleException(

                    "ORDER_NOT_RECOVERABLE",

                    "This order cannot be reconciled automatically."
            );
        }

        if (order.getRazorpayOrderId() == null

                || order.getRazorpayOrderId().isBlank()) {

            throw new BusinessRuleException(

                    "GATEWAY_ORDER_MISSING",

                    "Razorpay order reference is missing."
            );
        }

        List<PaymentResponse> gatewayPayments =

                razorpayService.fetchOrderPayments(
                        order.getRazorpayOrderId()
                );

        for (PaymentResponse payment : gatewayPayments) {

            if (!payment.isCaptured()) {
                continue;
            }

            if (payment.amountRefunded() > 0) {
                continue;
            }

            if (payment.amount() != order.getPayablePaise()) {
                continue;
            }

            if (!order.getCurrency().equals(payment.currency())) {
                continue;
            }

            if (!order.getRazorpayOrderId()
                    .equals(payment.orderId())) {
                continue;
            }

            completionService.completeFromWebhook(

                    order.getRazorpayOrderId(),

                    payment.id()
            );

            PaymentOrder updated = getOrder(orderId);

            return response(

                    updated,

                    "Payment verified and membership activated."
            );
        }

        return response(

                getOrder(orderId),

                "No matching captured payment found. "
                        + "Order remains unchanged."
        );
    }

    private ReconciliationResponse response(

            PaymentOrder order,

            String message

    ) {

        return new ReconciliationResponse(

                order.getId(),

                order.getOrderReference(),

                order.getStatus().name(),

                message
        );
    }
}