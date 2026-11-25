package com.smartlibrary.event;

import com.smartlibrary.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanEmailListener {

    private final EmailService emailService;

    // ==========================================
    // BORROW CONFIRMATION EMAIL
    // ==========================================

    @Async
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleBookBorrowed(
            BookBorrowedEvent event
    ) {

        try {

            emailService.sendBorrowConfirmation(
                    event.email(),
                    event.memberName(),
                    event.bookTitle(),
                    event.borrowDate(),
                    event.dueDate()
            );

            log.info(
                    "Book borrow confirmation email sent"
            );

        } catch (Exception exception) {

            log.error(
                    "Failed to send borrow confirmation email",
                    exception
            );
        }
    }

    // ==========================================
    // RETURN / RENEW / LOST EMAIL
    // ==========================================

    @Async
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleLoanNotification(
            LoanNotificationEvent event
    ) {

        try {

            emailService.sendLoanNotification(
                    event.email(),
                    event.memberName(),
                    event.bookTitle(),
                    event.subject(),
                    event.message(),
                    event.eventDate(),
                    event.dueDate(),
                    event.status()
            );

            log.info(
                    "Loan notification email sent: {}",
                    event.subject()
            );

        } catch (Exception exception) {

            log.error(
                    "Failed to send loan notification email",
                    exception
            );
        }
    }
}