package com.smartlibrary.listener;

import com.smartlibrary.event.TicketReplyEvent;

import com.smartlibrary.service.ReminderMailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Component;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketEmailListener {

    private final ReminderMailService mailService;

    @Value("${library.support.email-enabled:true}")
    private boolean emailEnabled;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleTicketReply(
            TicketReplyEvent event
    ) {

        if (!emailEnabled) {
            return;
        }

        if (event.memberEmail() == null

                || event.memberEmail().isBlank()) {

            return;
        }

        String subject =
                "Support Ticket Reply - "
                        + event.ticketNumber();

        String message =

                "Our library support team has replied "
                        + "to your support ticket.\n\n"

                        + "Ticket Number: "
                        + event.ticketNumber()

                        + "\nSubject: "
                        + event.subject()

                        + "\n\nPlease log in to your Smart Library "
                        + "account to view the reply and "
                        + "continue the conversation.";

        try {

            mailService.sendEmail(

                    event.memberEmail(),

                    event.memberName(),

                    subject,

                    message
            );

            log.info(

                    "Support reply email sent for ticket {}",

                    event.ticketNumber()
            );

        } catch (Exception e) {

            log.warn(

                    "Support reply email failed for ticket {}",

                    event.ticketNumber(),

                    e
            );
        }
    }
}