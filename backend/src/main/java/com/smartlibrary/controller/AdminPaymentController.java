package com.smartlibrary.controller;

import com.smartlibrary.dto.AdminPaymentDto.PaymentSummary;
import com.smartlibrary.dto.AdminPaymentDto.RefundResponse;
import com.smartlibrary.dto.AdminPaymentDto.RefundRequest;
import com.smartlibrary.dto.AdminPaymentDto.ReconciliationResponse;

import com.smartlibrary.entity.DemoPayment;
import com.smartlibrary.entity.PaymentOrder;
import com.smartlibrary.entity.PaymentOrderStatus;
import com.smartlibrary.entity.RefundStatus;

import com.smartlibrary.security.UserPrincipal;

import com.smartlibrary.service.AdminPaymentService;
import com.smartlibrary.service.PaymentRefundService;
import com.smartlibrary.service.RefundReconciliationService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final AdminPaymentService paymentService;

    private final PaymentRefundService refundService;

    private final RefundReconciliationService reconciliationService;

    // ==========================================
    // ALL PAYMENTS
    // ==========================================

    @GetMapping
    public List<DemoPayment> getAllPayments() {

        return paymentService.getAllPayments();
    }

    // ==========================================
    // PAYMENT SUMMARY
    // ==========================================

    @GetMapping("/summary")
    public PaymentSummary getSummary() {

        return paymentService.getSummary();
    }

    // ==========================================
    // PAYMENT ORDERS
    // ==========================================

    @GetMapping("/orders")
    public Page<PaymentOrder> getOrders(

            @RequestParam(required = false)
            PaymentOrderStatus status,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size

    ) {

        return paymentService.getOrders(

                status,

                pageRequest(page, size)
        );
    }

    // ==========================================
    // SINGLE ORDER
    // ==========================================

    @GetMapping("/orders/{orderId}")
    public PaymentOrder getOrder(

            @PathVariable Long orderId

    ) {

        return paymentService.getOrder(orderId);
    }

    // ==========================================
    // PAYMENT RECONCILIATION
    // ==========================================

    @PostMapping("/orders/{orderId}/reconcile")
    public ReconciliationResponse reconcile(

            @PathVariable Long orderId

    ) {

        return paymentService.reconcile(orderId);
    }

    // ==========================================
    // REQUEST REFUND
    // ==========================================

    @PostMapping("/{paymentId}/refund")
    public RefundResponse requestRefund(

            @PathVariable Long paymentId,

            @Valid
            @RequestBody RefundRequest request,

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return refundService.requestRefund(

                paymentId,

                principal.getId(),

                request
        );
    }

    // ==========================================
    // REFUND HISTORY
    // ==========================================

    @GetMapping("/refunds")
    public Page<RefundResponse> getRefunds(

            @RequestParam(required = false)
            RefundStatus status,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size

    ) {

        return paymentService.getRefunds(

                status,

                pageRequest(page, size)
        );
    }

    // ==========================================
    // REFUND DETAILS
    // ==========================================

    @GetMapping("/refunds/{refundId}")
    public RefundResponse getRefund(

            @PathVariable Long refundId

    ) {

        return refundService.getRefund(refundId);
    }

    // ==========================================
    // REFUND BY PAYMENT
    // ==========================================

    @GetMapping("/{paymentId}/refund")
    public RefundResponse getRefundByPayment(

            @PathVariable Long paymentId

    ) {

        return refundService.getRefundByPayment(
                paymentId
        );
    }

    // ==========================================
    // MANUAL REFUND RECONCILIATION
    // ==========================================

    @PostMapping("/refunds/{refundId}/reconcile")
    public RefundResponse reconcileRefund(

            @PathVariable Long refundId

    ) {

        return reconciliationService.reconcile(

                refundId
        );
    }

    // ==========================================
    // PAGINATION
    // ==========================================

    private PageRequest pageRequest(

            int page,

            int size

    ) {

        return PageRequest.of(

                Math.max(0, page),

                Math.max(1, Math.min(size, 100))
        );
    }
}