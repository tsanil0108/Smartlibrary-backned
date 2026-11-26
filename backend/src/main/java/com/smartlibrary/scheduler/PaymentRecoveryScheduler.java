package com.smartlibrary.scheduler;

import com.smartlibrary.entity.PaymentOrder;
import com.smartlibrary.entity.PaymentOrderStatus;

import com.smartlibrary.repository.PaymentOrderRepository;

import com.smartlibrary.service.PaymentCompletionService;
import com.smartlibrary.service.RazorpayService;

import com.smartlibrary.dto.RazorpayDto.PaymentResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentOrderRepository orders;

    private final RazorpayService razorpayService;

    private final PaymentCompletionService completionService;

    private final Clock clock;

    private static final int BATCH_SIZE = 50;

    private Long lastProcessedId = 0L;

    private static final List<PaymentOrderStatus>
            RECOVERABLE_STATUSES = List.of(

            PaymentOrderStatus.PENDING,

            PaymentOrderStatus.CAPTURED
    );

    @Scheduled(
            initialDelay = 60000,
            fixedDelay = 300000
    )
    public void recoverPendingPayments() {

        if (!razorpayService.isConfigured()) {

            return;
        }

        List<PaymentOrder> pendingOrders =
                orders.findByIdGreaterThanAndStatusInAndRazorpayOrderIdIsNotNullOrderByIdAsc(

                        lastProcessedId,

                        RECOVERABLE_STATUSES,

                        PageRequest.of(0, BATCH_SIZE)

                ).getContent();

        if (pendingOrders.isEmpty()) {

            lastProcessedId = 0L;

            return;
        }

        for (PaymentOrder order : pendingOrders) {

            try {

                recoverOrder(order);

            } catch (Exception ex) {

                log.warn(

                        "Payment recovery failed. Order ID: {}, error: {}",

                        order.getId(),

                        ex.getClass().getSimpleName()
                );

            } finally {

                lastProcessedId = order.getId();
            }
        }
    }

    private void recoverOrder(
            PaymentOrder order
    ) {

        List<PaymentResponse> gatewayPayments =
                razorpayService.fetchOrderPayments(

                        order.getRazorpayOrderId()
                );

        for (PaymentResponse payment : gatewayPayments) {

            if (!payment.isCaptured()) {

                continue;
            }

            if (payment.amountRefunded() > 0) {

                log.warn(
                        "Refunded payment requires reconciliation. Order ID: {}",
                        order.getId()
                );

                continue;
            }

            if (payment.amount()
                    != order.getPayablePaise()) {

                log.warn(
                        "Payment amount mismatch. Order ID: {}",
                        order.getId()
                );

                continue;
            }

            if (!"INR".equals(payment.currency())) {

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

            log.info(

                    "Payment recovered successfully. Order ID: {}",

                    order.getId()
            );

            return;
        }

        // Do not release a reserved coupon just because
        // the local order expiration time has passed.
        //
        // A gateway order may still receive a late payment.

        if (order.getExpiresAt() != null

                && order.getExpiresAt().isBefore(
                Instant.now(clock)
        )) {

            log.debug(

                    "Unpaid expired local order awaiting reconciliation: {}",

                    order.getId()
            );
        }
    }
}