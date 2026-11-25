package com.smartlibrary.listener;

import com.smartlibrary.event.ReservationReadyEvent;
import com.smartlibrary.service.EmailReminderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEmailListener {

    private final EmailReminderService reminderService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleReservationReady(
            ReservationReadyEvent event
    ) {

        try {

            reminderService.sendReservationReady(

                    event.reservationId(),

                    event.userId(),

                    event.email(),

                    event.memberName(),

                    event.bookTitle(),

                    event.expiresAt()
            );

        } catch (Exception e) {

            log.error(
                    "Reservation email processing failed: {}",
                    event.reservationId(),
                    e
            );
        }
    }
}