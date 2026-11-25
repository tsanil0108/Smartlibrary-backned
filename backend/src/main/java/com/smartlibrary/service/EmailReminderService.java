package com.smartlibrary.service;

import com.smartlibrary.config.EmailReminderProperties;
import com.smartlibrary.config.FineProperties;

import com.smartlibrary.entity.EmailReminderLog;
import com.smartlibrary.entity.EmailReminderType;
import com.smartlibrary.entity.Loan;
import com.smartlibrary.entity.LoanStatus;
import com.smartlibrary.entity.Membership;
import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;
import com.smartlibrary.entity.UserStatus;

import com.smartlibrary.repository.EmailReminderLogRepository;
import com.smartlibrary.repository.LoanRepository;
import com.smartlibrary.repository.MembershipRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailReminderService {

    private final LoanRepository loanRepository;

    private final MembershipRepository membershipRepository;

    private final EmailReminderLogRepository reminderRepository;

    private final ReminderMailService mailService;

    private final EmailReminderProperties properties;

    private final FineProperties fineProperties;

    private final Clock clock;

    // ==========================================
    // PROCESS ALL ACTIVE LOAN REMINDERS
    // ==========================================

    @Transactional
    public void sendLoanReminders() {

        if (!properties.isEnabled()) {
            return;
        }

        LocalDate today = today();

        List<Loan> loans =
                loanRepository.findByStatus(
                        LoanStatus.ACTIVE
                );

        for (Loan loan : loans) {

            User user = loan.getUser();

            if (!canReceiveEmail(user)) {
                continue;
            }

            long remainingDays =
                    ChronoUnit.DAYS.between(
                            today,
                            loan.getDueDate()
                    );

            if (remainingDays < 0) {

                sendOverdueReminder(
                        loan,
                        Math.abs(remainingDays),
                        today
                );

            } else if (remainingDays == 0) {

                sendDueTodayReminder(
                        loan,
                        today
                );

            } else if (
                    remainingDays
                            == properties.getDueBeforeDays()
            ) {

                sendUpcomingReminder(
                        loan,
                        remainingDays,
                        today
                );
            }
        }
    }

    // ==========================================
    // UPCOMING BOOK DUE DATE
    // ==========================================

    private void sendUpcomingReminder(
            Loan loan,
            long remainingDays,
            LocalDate today
    ) {

        User user = loan.getUser();

        String bookTitle =
                loan.getCopy()
                        .getBook()
                        .getTitle();

        String subject =
                "Book Return Reminder - " + bookTitle;

        String message =

                "Your borrowed book is due in "
                        + remainingDays + " day(s)."

                        + "\n\nBook: " + bookTitle

                        + "\nIssue Date: "
                        + loan.getIssueDate()

                        + "\nDue Date: "
                        + loan.getDueDate()

                        + "\n\nPlease return or renew your book "
                        + "before the due date to avoid late fees.";

        sendOnce(

                user.getId(),

                loan.getId(),

                user.getEmail(),

                user.getName(),

                EmailReminderType.BOOK_DUE_SOON,

                subject,

                message,

                today
        );
    }

    // ==========================================
    // BOOK DUE TODAY
    // ==========================================

    private void sendDueTodayReminder(
            Loan loan,
            LocalDate today
    ) {

        User user = loan.getUser();

        String bookTitle =
                loan.getCopy()
                        .getBook()
                        .getTitle();

        String subject =
                "Your Library Book Is Due Today";

        String message =

                "Your borrowed book is due today."

                        + "\n\nBook: " + bookTitle

                        + "\nDue Date: "
                        + loan.getDueDate()

                        + "\n\nPlease return your book today "
                        + "or renew it if eligible.";

        sendOnce(

                user.getId(),

                loan.getId(),

                user.getEmail(),

                user.getName(),

                EmailReminderType.BOOK_DUE_TODAY,

                subject,

                message,

                today
        );
    }

    // ==========================================
    // OVERDUE BOOK REMINDER
    // ==========================================

    private void sendOverdueReminder(
            Loan loan,
            long overdueDays,
            LocalDate today
    ) {

        User user = loan.getUser();

        String bookTitle =
                loan.getCopy()
                        .getBook()
                        .getTitle();

        BigDecimal estimatedFine =
                fineProperties.getOverduePerDay()

                        .multiply(
                                BigDecimal.valueOf(overdueDays)
                        )

                        .min(
                                fineProperties.getMaxOverdueFine()
                        )

                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        String subject =
                "Overdue Library Book - " + bookTitle;

        String message =

                "Your borrowed book is overdue."

                        + "\n\nBook: " + bookTitle

                        + "\nDue Date: "
                        + loan.getDueDate()

                        + "\nOverdue Days: "
                        + overdueDays

                        + "\nFine Per Day: Rs. "
                        + fineProperties.getOverduePerDay()

                        + "\nEstimated Fine: Rs. "
                        + estimatedFine

                        + "\n\nPlease return your book "
                        + "as soon as possible.";

        sendOnce(

                user.getId(),

                loan.getId(),

                user.getEmail(),

                user.getName(),

                EmailReminderType.BOOK_OVERDUE,

                subject,

                message,

                today
        );
    }

    // ==========================================
    // PROCESS MEMBERSHIP REMINDERS
    // ==========================================

    @Transactional
    public void sendMembershipReminders() {

        if (!properties.isEnabled()) {
            return;
        }

        LocalDate today = today();

        int reminderDays = Math.max(
                1,
                properties.getMembershipBeforeDays()
        );

        // Upcoming membership expiry

        List<Membership> expiringMemberships =
                membershipRepository.findExpiringMemberships(

                        today,

                        today.plusDays(reminderDays)
                );

        for (Membership membership : expiringMemberships) {

            User user = membership.getUser();

            if (!canReceiveEmail(user)) {
                continue;
            }

            long remainingDays =
                    ChronoUnit.DAYS.between(

                            today,

                            membership.getEndDate()
                    );

            if (remainingDays != reminderDays
                    && remainingDays != 1) {

                continue;
            }

            // Skip if member has renewed continuously

            if (hasReplacementMembership(membership)) {

                continue;
            }

            sendMembershipReminder(

                    membership.getId(),

                    user.getId(),

                    user.getEmail(),

                    user.getName(),

                    membership.getPlanName(),

                    membership.getEndDate()
            );
        }

        // Recently expired memberships

        List<Membership> expiredMemberships =
                membershipRepository
                        .findRecentlyExpiredMemberships(
                                today.minusDays(1)
                        );

        Set<Long> processedUsers = new HashSet<>();

        for (Membership membership : expiredMemberships) {

            User user = membership.getUser();

            if (!canReceiveEmail(user)) {
                continue;
            }

            if (processedUsers.contains(user.getId())) {
                continue;
            }

            // Do not send expiry email if currently active

            boolean hasActiveMembership =
                    !membershipRepository
                            .findActiveOn(
                                    user.getId(),
                                    today
                            )
                            .isEmpty();

            if (hasActiveMembership
                    || hasReplacementMembership(membership)) {

                continue;
            }

            processedUsers.add(user.getId());

            sendMembershipReminder(

                    membership.getId(),

                    user.getId(),

                    user.getEmail(),

                    user.getName(),

                    membership.getPlanName(),

                    membership.getEndDate()
            );
        }
    }

    // ==========================================
    // CHECK IF MEMBERSHIP HAS BEEN RENEWED
    // ==========================================

    private boolean hasReplacementMembership(
            Membership membership
    ) {

        return membershipRepository
                .countReplacementMemberships(

                        membership.getUser().getId(),

                        membership.getId(),

                        membership.getEndDate(),

                        membership.getEndDate().plusDays(1)

                ) > 0;
    }

    // ==========================================
    // MEMBERSHIP EXPIRY EMAIL
    // ==========================================

    @Transactional
    public void sendMembershipReminder(

            Long membershipId,

            Long userId,

            String email,

            String memberName,

            String planName,

            LocalDate endDate

    ) {

        if (!properties.isEnabled()) {
            return;
        }

        LocalDate today = today();

        long remainingDays =
                ChronoUnit.DAYS.between(
                        today,
                        endDate
                );

        // Membership expires soon

        if (remainingDays
                == properties.getMembershipBeforeDays()
                || remainingDays == 1) {

            String subject =
                    "Your Library Membership Is Expiring";

            String message =

                    "Your library membership will expire in "
                            + remainingDays + " day(s)."

                            + "\n\nMembership Plan: "
                            + planName

                            + "\nExpiry Date: "
                            + endDate

                            + "\n\nPlease renew your membership "
                            + "to continue borrowing books.";

            sendOnce(

                    userId,

                    membershipId,

                    email,

                    memberName,

                    EmailReminderType.MEMBERSHIP_EXPIRING,

                    subject,

                    message,

                    today
            );

        } else if (remainingDays == -1) {

            String subject =
                    "Your Library Membership Has Expired";

            String message =

                    "Your library membership has expired."

                            + "\n\nMembership Plan: "
                            + planName

                            + "\nExpiry Date: "
                            + endDate

                            + "\n\nPlease renew your membership "
                            + "to restore borrowing access.";

            sendOnce(

                    userId,

                    membershipId,

                    email,

                    memberName,

                    EmailReminderType.MEMBERSHIP_EXPIRED,

                    subject,

                    message,

                    today
            );
        }
    }

    // ==========================================
    // RESERVED BOOK AVAILABLE EMAIL
    // ==========================================

    @Transactional
    public void sendReservationReady(

            Long reservationId,

            Long userId,

            String email,

            String memberName,

            String bookTitle,

            LocalDateTime expiresAt

    ) {

        if (!properties.isEnabled()) {
            return;
        }

        String subject =
                "Your Reserved Book Is Available";

        String message =

                "Good news! Your reserved book is now available."

                        + "\n\nBook: " + bookTitle

                        + "\nCollect Before: "
                        + expiresAt.toLocalDate()

                        + " "
                        + expiresAt.toLocalTime()

                        + "\n\nPlease borrow the book "
                        + "before your reservation expires.";

        sendOnce(

                userId,

                reservationId,

                email,

                memberName,

                EmailReminderType.RESERVATION_READY,

                subject,

                message,

                today()
        );
    }

    // ==========================================
    // SEND EMAIL WITH DUPLICATE PROTECTION
    // ==========================================

    private synchronized void sendOnce(

            Long userId,

            Long referenceId,

            String email,

            String memberName,

            EmailReminderType type,

            String subject,

            String message,

            LocalDate reminderDate

    ) {

        if (email == null || email.isBlank()) {
            return;
        }

        String reminderKey =

                type.name()

                        + ":" + userId

                        + ":" + referenceId

                        + ":" + reminderDate;

        // Skip already sent reminders

        if (reminderRepository
                .existsByReminderKeyAndSentTrue(
                        reminderKey
                )) {

            return;
        }

        // Find existing failed attempt or create new log

        EmailReminderLog reminder =
                reminderRepository
                        .findByReminderKey(reminderKey)

                        .orElseGet(() -> {

                            EmailReminderLog entry =
                                    new EmailReminderLog();

                            entry.setReminderKey(
                                    reminderKey
                            );

                            entry.setUserId(userId);

                            entry.setReferenceId(
                                    referenceId
                            );

                            entry.setType(type);

                            entry.setRecipient(email);

                            entry.setSubject(subject);

                            entry.setReminderDate(
                                    reminderDate
                            );

                            entry.setCreatedAt(now());

                            return entry;
                        });

        reminder.setAttempts(
                reminder.getAttempts() + 1
        );

        reminder.setRecipient(email);

        reminder.setSubject(subject);

        try {

            mailService.sendEmail(

                    email,

                    memberName,

                    subject,

                    message
            );

            reminder.setSent(true);

            reminder.setSentAt(now());

            reminder.setErrorMessage(null);

            log.info(

                    "Email sent successfully: "
                            + "type={}, userId={}",

                    type,

                    userId
            );

        } catch (Exception e) {

            reminder.setSent(false);

            reminder.setErrorMessage(
                    e.getClass().getSimpleName()
            );

            log.warn(

                    "Email sending failed: "
                            + "type={}, userId={}, error={}",

                    type,

                    userId,

                    e.getClass().getSimpleName()
            );
        }

        reminderRepository.saveAndFlush(reminder);
    }

    // ==========================================
    // VALIDATE EMAIL RECIPIENT
    // ==========================================

    private boolean canReceiveEmail(User user) {

        return user != null

                && user.getRole() == Role.MEMBER

                && user.getStatus() == UserStatus.ACTIVE

                && user.getEmail() != null

                && !user.getEmail().isBlank();
    }

    // ==========================================
    // CURRENT DATE AND TIME
    // ==========================================

    private LocalDate today() {

        return LocalDate.now(

                clock.withZone(
                        ZoneId.of(properties.getZone())
                )
        );
    }

    private LocalDateTime now() {

        return LocalDateTime.now(

                clock.withZone(
                        ZoneId.of(properties.getZone())
                )
        );
    }
}