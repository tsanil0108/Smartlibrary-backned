package com.smartlibrary.event;

import com.smartlibrary.config.PaymentProperties;
import com.smartlibrary.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConfirmationListener {

    private final EmailService emailService;

    private final PaymentProperties properties;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy",
                    Locale.ENGLISH
            );

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handlePaymentConfirmation(
            PaymentConfirmationEvent event
    ) {

        if (!properties.isConfirmationEmailEnabled()) {
            return;
        }

        try {

            String html = buildEmail(event);

            emailService.sendHtmlEmail(

                    event.email(),

                    "Membership Activated | Smart Library",

                    html
            );

            log.info(
                    "Payment confirmation email sent. Payment ID: {}",
                    event.paymentId()
            );

        } catch (Exception ex) {

            log.warn(
                    "Payment confirmation email failed. Payment ID: {}",
                    event.paymentId()
            );
        }
    }

    private String buildEmail(
            PaymentConfirmationEvent event
    ) {

        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:24px;
                    background:#f5f5fb;
                    font-family:Arial,sans-serif;">

                <div style="max-width:600px;
                    margin:auto;background:white;
                    border-radius:16px;overflow:hidden;">

                    <div style="background:#17143d;
                        padding:30px;color:white;">

                        <h1 style="margin:0;">
                            Smart Library
                        </h1>

                        <p style="color:#ffad57;">
                            Premium Membership
                        </p>

                    </div>

                    <div style="padding:30px;">

                        <h2 style="color:#15966a;">
                            Membership Activated!
                        </h2>

                        <p>Hello %s,</p>

                        <p>Your membership has been
                        successfully activated.</p>

                        <table style="width:100%%;
                            border-collapse:collapse;
                            line-height:2.2;">

                            <tr>
                                <td>Membership Plan</td>
                                <td><strong>%s</strong></td>
                            </tr>

                            <tr>
                                <td>Transaction ID</td>
                                <td>%s</td>
                            </tr>

                            <tr>
                                <td>Payment Method</td>
                                <td>%s</td>
                            </tr>

                            <tr>
                                <td>Original Price</td>
                                <td>%s</td>
                            </tr>

                            <tr>
                                <td>Discount</td>
                                <td>%s</td>
                            </tr>

                            <tr>
                                <td>Amount Paid</td>
                                <td><strong>%s</strong></td>
                            </tr>

                            <tr>
                                <td>Start Date</td>
                                <td>%s</td>
                            </tr>

                            <tr>
                                <td>Valid Until</td>
                                <td>%s</td>
                            </tr>

                        </table>

                        <hr style="border:0;
                            border-top:1px solid #eeeeee;">

                        <h3>Membership Benefits</h3>

                        <p>✓ Borrow library books</p>
                        <p>✓ Reserve books</p>
                        <p>✓ Digital library card</p>
                        <p>✓ Reading analytics</p>

                        <p>You can download your receipt
                        from the Payment History section
                        of your member account.</p>

                        <p>Thank you for choosing
                        Smart Library.</p>

                    </div>

                    <div style="background:#f4f3fa;
                        padding:20px;text-align:center;
                        color:#777777;font-size:12px;">

                        Smart Library Management System
                        <br>
                        This is an automated email.

                    </div>

                </div>

                </body>
                </html>
                """.formatted(

                escape(event.memberName()),

                escape(event.planName()),

                escape(event.transactionId()),

                escape(event.paymentMethod()),

                money(event.originalPaise()),

                money(event.discountPaise()),

                money(event.paidPaise()),

                date(event.startDate()),

                date(event.endDate())
        );
    }

    private String escape(String value) {

        return HtmlUtils.htmlEscape(
                value == null ? "-" : value
        );
    }

    private String money(long paise) {

        return "₹" + BigDecimal
                .valueOf(paise, 2)
                .toPlainString();
    }

    private String date(LocalDate value) {

        return value == null
                ? "-"
                : value.format(DATE_FORMAT);
    }
}