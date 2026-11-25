package com.smartlibrary.service;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.dto.LoanDto.LoanResponse;
import com.smartlibrary.dto.LoanDto.ReturnCondition;
import com.smartlibrary.dto.PageResponse;

import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.BookCopy;
import com.smartlibrary.entity.CopyStatus;
import com.smartlibrary.entity.Loan;
import com.smartlibrary.entity.LoanStatus;
import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;
import com.smartlibrary.entity.UserStatus;

import com.smartlibrary.event.BookBorrowedEvent;
import com.smartlibrary.event.LoanNotificationEvent;

import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.ConflictException;
import com.smartlibrary.exception.NotFoundException;

import com.smartlibrary.repository.BookCopyRepository;
import com.smartlibrary.repository.LoanRepository;
import com.smartlibrary.repository.UserRepository;

import com.smartlibrary.security.UserPrincipal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepository;
    private final BookCopyRepository copyRepository;
    private final UserRepository userRepository;
    private final MembershipService membershipService;
    private final AuditService auditService;
    private final LibraryProperties rules;
    private final Clock clock;

    // Email notification system
    private final ApplicationEventPublisher eventPublisher;

    // Reservation system
    private final ReservationService reservationService;

    // Automatic fine management system
    private final FineService fineService;

    // ==========================================
    // BORROW BOOK
    // ==========================================

    @Transactional
    public LoanResponse borrow(
            Long userId,
            Long bookId,
            Long issuedByAdminId
    ) {

        // Lock member to prevent concurrent borrowing

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "User",
                                userId
                        )
                );

        // Validate member role

        if (user.getRole() != Role.MEMBER) {

            throw new BusinessRuleException(
                    "NOT_A_MEMBER",
                    "Only members can borrow books"
            );
        }

        // Validate account status

        if (user.getStatus() != UserStatus.ACTIVE) {

            throw new BusinessRuleException(
                    "ACCOUNT_SUSPENDED",
                    "This account is suspended"
            );
        }

        LocalDate today = LocalDate.now(clock);

        // Validate active membership

        membershipService.requireActiveMembership(
                userId,
                today
        );

        // Block borrowing if pending fines exist

        fineService.requireNoPendingFine(userId);

        // Lock book for reservation and borrowing

        Book book = reservationService.lockBook(bookId);

        // Prevent borrowing archived books

        if (book.isArchived()) {

            throw new NotFoundException(
                    "Book",
                    bookId
            );
        }

        // Prevent duplicate borrowing

        if (loanRepository
                .existsByUser_IdAndCopy_Book_IdAndStatus(
                        userId,
                        bookId,
                        LoanStatus.ACTIVE
                )) {

            throw new ConflictException(
                    "DUPLICATE_LOAN",
                    "You already have an active loan for this book"
            );
        }

        // Validate maximum borrowing limit

        if (loanRepository.countByUser_IdAndStatus(
                userId,
                LoanStatus.ACTIVE
        ) >= rules.maxActiveLoans()) {

            throw new BusinessRuleException(
                    "LOAN_LIMIT_REACHED",
                    "Borrowing limit reached ("
                            + rules.maxActiveLoans()
                            + " active loans)"
            );
        }

        // Block borrowing when overdue loans exist

        if (rules.blockOnOverdue()
                && loanRepository
                .existsByUser_IdAndStatusAndDueDateBefore(
                        userId,
                        LoanStatus.ACTIVE,
                        today
                )) {

            throw new BusinessRuleException(
                    "OVERDUE_BLOCK",
                    "Return your overdue books before borrowing again"
            );
        }

        // Check reservation priority

        reservationService.checkBorrowAllowed(
                userId,
                bookId
        );

        // Lock and claim available book copy

        BookCopy copy = copyRepository
                .findAvailableForUpdate(
                        bookId,
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .orElseThrow(
                        () -> new ConflictException(
                                "NO_COPY_AVAILABLE",
                                "No copy of this book is available right now"
                        )
                );

        // Update book copy status

        copy.setStatus(
                CopyStatus.BORROWED
        );

        // Create new loan

        Loan loan = new Loan();

        loan.setUser(user);

        loan.setCopy(copy);

        loan.setIssueDate(today);

        loan.setDueDate(
                today.plusDays(
                        rules.loanDays()
                )
        );

        loan.setStatus(
                LoanStatus.ACTIVE
        );

        loanRepository.save(loan);

        // Complete READY reservation if available

        reservationService.completeReservation(
                userId,
                bookId
        );

        // Admin audit log

        if (issuedByAdminId != null) {

            auditService.log(
                    issuedByAdminId,

                    "LOAN_ISSUED_BY_ADMIN",

                    "LOAN",

                    loan.getId(),

                    "user=" + userId
                            + " book=" + book.getTitle()
            );
        }

        // Publish borrow confirmation event

        eventPublisher.publishEvent(

                new BookBorrowedEvent(

                        user.getEmail(),

                        user.getName(),

                        book.getTitle(),

                        today,

                        loan.getDueDate()
                )
        );

        return LoanResponse.from(
                loan,
                today
        );
    }

    // ==========================================
    // RETURN BOOK
    // ==========================================

    @Transactional
    public LoanResponse returnLoan(
            Long loanId,
            UserPrincipal actor,
            ReturnCondition requested
    ) {

        // Find and lock existing loan

        Loan loan = loanRepository
                .findByIdForUpdate(loanId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Loan",
                                loanId
                        )
                );

        // Validate loan ownership

        assertOwnerOrAdmin(
                loan,
                actor
        );

        // Only active loans can be returned

        if (loan.getStatus() != LoanStatus.ACTIVE) {

            throw new ConflictException(
                    "LOAN_NOT_ACTIVE",
                    "This loan is already closed"
            );
        }

        // Only admin can mark damaged or lost books

        ReturnCondition condition =
                actor.isAdmin() && requested != null

                        ? requested

                        : ReturnCondition.GOOD;

        BookCopy copy = loan.getCopy();

        Long bookId = copy.getBook().getId();

        // Lock book before changing inventory

        reservationService.lockBook(bookId);

        LocalDateTime now =
                LocalDateTime.now(clock);

        // ======================================
        // HANDLE BOOK RETURN CONDITION
        // ======================================

        switch (condition) {

            // ----------------------------------
            // GOOD CONDITION
            // ----------------------------------

            case GOOD -> {

                loan.setStatus(
                        LoanStatus.RETURNED
                );

                loan.setReturnedAt(now);

                copy.setStatus(
                        CopyStatus.AVAILABLE
                );
            }

            // ----------------------------------
            // DAMAGED CONDITION
            // ----------------------------------

            case DAMAGED -> {

                loan.setStatus(
                        LoanStatus.RETURNED
                );

                loan.setReturnedAt(now);

                copy.setStatus(
                        CopyStatus.DAMAGED
                );

                auditService.log(

                        actor.getId(),

                        "COPY_RETURNED_DAMAGED",

                        "COPY",

                        copy.getId(),

                        "loan=" + loanId
                );
            }

            // ----------------------------------
            // LOST CONDITION
            // ----------------------------------

            case LOST -> {

                loan.setStatus(
                        LoanStatus.LOST
                );

                // Record loan closure date

                loan.setReturnedAt(now);

                copy.setStatus(
                        CopyStatus.LOST
                );

                auditService.log(

                        actor.getId(),

                        "COPY_MARKED_LOST",

                        "COPY",

                        copy.getId(),

                        "loan=" + loanId
                );
            }
        }

        // ======================================
        // AUTOMATIC FINE MANAGEMENT
        // ======================================

        /*
         * Automatically calculates:
         *
         * 1. Overdue fine
         * 2. Damaged book charges
         * 3. Lost book charges
         *
         * FineService prevents duplicate fines.
         */

        fineService.createFinesForReturn(
                loan,
                condition
        );

        // Save loan and book copy changes

        loanRepository.saveAndFlush(loan);

        // ======================================
        // RESERVATION QUEUE MANAGEMENT
        // ======================================

        // Only good condition books become available

        if (condition == ReturnCondition.GOOD) {

            reservationService.onBookAvailable(
                    bookId
            );
        }

        // ======================================
        // RETURN EMAIL NOTIFICATION
        // ======================================

        String subject;

        String message;

        if (condition == ReturnCondition.LOST) {

            subject =
                    "Library Book Marked as Lost";

            message =
                    "Your borrowed book has been marked "
                            + "as lost. Please check your "
                            + "library fine dashboard for "
                            + "applicable charges.";

        } else if (condition == ReturnCondition.DAMAGED) {

            subject =
                    "Book Returned - Damage Recorded";

            message =
                    "Your book has been returned. "
                            + "Damage was recorded during "
                            + "the return. Please check your "
                            + "fine dashboard for applicable charges.";

        } else {

            subject =
                    "Book Returned Successfully";

            message =
                    "Your book has been returned successfully. "
                            + "If the book was overdue, please "
                            + "check your fine dashboard. "
                            + "Thank you for using Smart Library.";
        }

        // Publish return notification event

        publishLoanNotification(

                loan,

                subject,

                message,

                now.toLocalDate()
        );

        return LoanResponse.from(

                loan,

                now.toLocalDate()
        );
    }

    // ==========================================
    // RENEW BOOK
    // ==========================================

    @Transactional
    public LoanResponse renew(
            Long loanId,
            UserPrincipal actor
    ) {

        // Find and lock loan

        Loan loan = loanRepository
                .findByIdForUpdate(loanId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Loan",
                                loanId
                        )
                );

        // Validate ownership

        assertOwnerOrAdmin(
                loan,
                actor
        );

        // Only active loans can be renewed

        if (loan.getStatus() != LoanStatus.ACTIVE) {

            throw new ConflictException(
                    "LOAN_NOT_ACTIVE",
                    "Only active loans can be renewed"
            );
        }

        LocalDate today =
                LocalDate.now(clock);

        // Overdue loans cannot be renewed

        if (loan.getDueDate().isBefore(today)) {

            throw new BusinessRuleException(
                    "LOAN_OVERDUE",
                    "Overdue loans cannot be renewed"
            );
        }

        // Validate maximum renewal limit

        if (loan.getRenewCount()
                >= rules.maxRenewals()) {

            throw new BusinessRuleException(
                    "RENEWAL_LIMIT",
                    "Renewal limit reached ("
                            + rules.maxRenewals()
                            + ")"
            );
        }

        // Validate membership

        membershipService.requireActiveMembership(

                loan.getUser().getId(),

                today
        );

        // Prevent renewal if another member is waiting

        reservationService.checkRenewalAllowed(

                loan.getCopy()
                        .getBook()
                        .getId()
        );

        // Extend existing due date

        loan.setDueDate(

                loan.getDueDate()
                        .plusDays(
                                rules.renewalDays()
                        )
        );

        // Increase renewal count

        loan.setRenewCount(

                loan.getRenewCount() + 1
        );

        loanRepository.save(loan);

        // Publish renewal confirmation event

        publishLoanNotification(

                loan,

                "Book Renewed Successfully",

                "Your book loan has been renewed. "
                        + "Please check your updated due date.",

                today
        );

        return LoanResponse.from(
                loan,
                today
        );
    }

    // ==========================================
    // FIND LOANS
    // ==========================================

    @Transactional(readOnly = true)
    public PageResponse<LoanResponse> find(

            Long userId,

            LoanStatus status,

            Boolean overdue,

            Pageable pageable

    ) {

        LocalDate today =
                LocalDate.now(clock);

        Page<Loan> page = loanRepository.findAll(

                LoanSpecs.filter(

                        userId,

                        status,

                        overdue,

                        today
                ),

                pageable
        );

        List<LoanResponse> content = page

                .getContent()

                .stream()

                .map(
                        loan -> LoanResponse.from(
                                loan,
                                today
                        )
                )

                .toList();

        return PageResponse.of(
                content,
                page
        );
    }

    // ==========================================
    // EMAIL NOTIFICATION HELPER
    // ==========================================

    private void publishLoanNotification(

            Loan loan,

            String subject,

            String message,

            LocalDate eventDate

    ) {

        User user = loan.getUser();

        eventPublisher.publishEvent(

                new LoanNotificationEvent(

                        user.getEmail(),

                        user.getName(),

                        loan.getCopy()
                                .getBook()
                                .getTitle(),

                        subject,

                        message,

                        eventDate,

                        loan.getDueDate(),

                        loan.getStatus().name()
                )
        );
    }

    // ==========================================
    // AUTHORIZATION
    // ==========================================

    private void assertOwnerOrAdmin(

            Loan loan,

            UserPrincipal actor

    ) {

        if (!actor.isAdmin()

                && !loan.getUser()
                .getId()
                .equals(actor.getId())) {

            throw new AccessDeniedException(
                    "Not your loan"
            );
        }
    }
}