package com.smartlibrary.scheduler;

import com.smartlibrary.entity.PaymentRefund;
import com.smartlibrary.entity.RefundStatus;

import com.smartlibrary.repository.PaymentRefundRepository;

import com.smartlibrary.service.RazorpayRefundService;
import com.smartlibrary.service.RefundReconciliationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRecoveryScheduler {

    private final PaymentRefundRepository refunds;

    private final RazorpayRefundService refundGateway;

    private final RefundReconciliationService reconciliationService;

    private static final int BATCH_SIZE = 50;

    private static final List<RefundStatus> RECOVERABLE_STATUSES =
            List.of(

                    RefundStatus.REQUESTED,

                    RefundStatus.PROCESSING,

                    RefundStatus.RECONCILIATION_REQUIRED,

                    RefundStatus.FAILED
            );

    private int pageNumber = 0;

    // ==========================================
    // AUTOMATIC REFUND RECOVERY
    // ==========================================

    @Scheduled(

            initialDelay = 60000,

            fixedDelay = 300000

    )
    public void recoverPendingRefunds() {

        if (!refundGateway.isConfigured()) {

            return;
        }

        Page<PaymentRefund> page = refunds

                .findByStatusInOrderByIdAsc(

                        RECOVERABLE_STATUSES,

                        PageRequest.of(
                                pageNumber,
                                BATCH_SIZE
                        )
                );

        if (page.isEmpty()) {

            pageNumber = 0;

            return;
        }

        for (PaymentRefund refund : page.getContent()) {

            try {

                var response = reconciliationService.reconcile(

                        refund.getId()
                );

                log.debug(

                        "Refund recovery: ID={}, status={}",

                        refund.getId(),

                        response.status()
                );

            } catch (RuntimeException ex) {

                log.warn(

                        "Refund recovery failed. ID={}, error={}",

                        refund.getId(),

                        ex.getClass().getSimpleName()
                );
            }
        }

        // Reset periodically so unresolved refunds
        // are checked again.

        pageNumber = page.hasNext()

                ? pageNumber + 1

                : 0;
    }
}