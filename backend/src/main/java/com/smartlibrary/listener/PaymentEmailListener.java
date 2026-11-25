package com.smartlibrary.listener;

import com.smartlibrary.event.PaymentCompletedEvent;

import com.smartlibrary.service.ReminderMailService;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEmailListener {

    private final ReminderMailService mailService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handlePaymentCompleted(
            PaymentCompletedEvent event
    ) {

        if (event.email() == null

                || event.email().isBlank()) {

            return;
        }

        String subject =
                "Membership Activated Successfully";

        String message =

                "Your library membership has been "
                        + "activated successfully."

                        + "\n\nTransaction ID: "
                        + event.transactionId()

                        + "\nMembership Plan: "
                        + event.membershipPlan()

                        + "\nCoupon Code: "
                        + event.couponCode()

                        + "\nOriginal Amount: Rs. "
                        + formatMoney(event.originalPaise())

                        + "\nCoupon Discount: Rs. "
                        + formatMoney(event.discountPaise())

                        + "\nAmount Paid: Rs. "
                        + formatMoney(event.paidPaise())

                        + "\nPayment Method: Demo Coupon"

                        + "\nMembership Start Date: "
                        + event.membershipStartDate()

                        + "\nMembership End Date: "
                        + event.membershipEndDate()

                        + "\n\nNo money was charged for "
                        + "this coupon redemption."

                        + "\n\nYou can now enjoy your "
                        + "library membership benefits.";

        try {

            mailService.sendEmail(

                    event.email(),

                    event.memberName(),

                    subject,

                    message
            );

            log.info(

                    "Payment confirmation email sent: {}",

                    event.transactionId()
            );

        } catch (Exception e) {

            log.warn(

                    "Payment confirmation email failed: {}, error={}",

                    event.transactionId(),

                    e.getClass().getSimpleName()
            );
        }
    }

    private String formatMoney(long paise) {

        return BigDecimal.valueOf(paise, 2)
                .toPlainString();
    }
}