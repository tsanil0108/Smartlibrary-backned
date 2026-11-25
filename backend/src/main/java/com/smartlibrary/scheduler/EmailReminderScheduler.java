package com.smartlibrary.scheduler;

import com.smartlibrary.config.EmailReminderProperties;

import com.smartlibrary.service.EmailReminderService;
import com.smartlibrary.service.MembershipService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailReminderScheduler {

    private final EmailReminderService reminderService;

    private final MembershipService membershipService;

    private final EmailReminderProperties properties;

    // ==========================================
    // AUTOMATIC DAILY REMINDER SCHEDULER
    // ==========================================

    @Scheduled(

            cron = "${library.reminders.cron:0 0 9 * * *}",

            zone = "${library.reminders.zone:Asia/Kolkata}"

    )
    public void processDailyReminders() {

        log.info(
                "Starting daily library maintenance"
        );

        // ======================================
        // UPDATE EXPIRED MEMBERSHIPS
        // ======================================

        try {

            int expiredCount =
                    membershipService
                            .expireEndedMemberships();

            log.info(

                    "Membership expiry processing completed. "
                            + "Expired memberships: {}",

                    expiredCount
            );

        } catch (Exception e) {

            log.error(

                    "Membership expiry processing failed",

                    e
            );
        }

        // Skip email processing if disabled

        if (!properties.isEnabled()) {

            log.info("Email reminders are disabled");

            return;
        }

        // ======================================
        // BOOK DUE DATE REMINDERS
        // ======================================

        try {

            reminderService.sendLoanReminders();

            log.info(
                    "Loan email reminders completed"
            );

        } catch (Exception e) {

            log.error(

                    "Loan reminder processing failed",

                    e
            );
        }

        // ======================================
        // MEMBERSHIP EXPIRY REMINDERS
        // ======================================

        try {

            reminderService.sendMembershipReminders();

            log.info(
                    "Membership email reminders completed"
            );

        } catch (Exception e) {

            log.error(

                    "Membership reminder processing failed",

                    e
            );
        }

        log.info(
                "Daily library maintenance completed"
        );
    }
}