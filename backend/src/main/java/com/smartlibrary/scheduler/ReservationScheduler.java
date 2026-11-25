package com.smartlibrary.scheduler;

import com.smartlibrary.service.ReservationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationScheduler {

    private final ReservationService reservationService;

    @Scheduled(fixedDelay = 300000)
    public void checkExpiredReservations() {

        try {

            reservationService.expireReservations();

        } catch (Exception e) {

            log.error(
                    "Reservation expiry check failed",
                    e
            );
        }
    }
}