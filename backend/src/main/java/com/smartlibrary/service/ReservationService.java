package com.smartlibrary.service;

import com.smartlibrary.dto.ReservationDto.ReservationResponse;
import com.smartlibrary.entity.*;
import com.smartlibrary.event.ReservationReadyEvent;
import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.ConflictException;
import com.smartlibrary.exception.NotFoundException;
import com.smartlibrary.repository.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final LoanRepository loanRepository;
    private final MembershipService membershipService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    private static final int MAX_RESERVATIONS = 3;

    private static final int HOLD_HOURS = 48;

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(
                    ReservationStatus.WAITING,
                    ReservationStatus.READY
            );

    // ==========================================
    // CREATE RESERVATION
    // ==========================================

    @Transactional
    public ReservationResponse reserve(
            Long userId,
            Long bookId
    ) {

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "User",
                                userId
                        )
                );

        // Validate member role and account status

        if (user.getRole() != Role.MEMBER
                || user.getStatus() != UserStatus.ACTIVE) {

            throw new BusinessRuleException(
                    "INVALID_MEMBER",
                    "Only active members can reserve books"
            );
        }

        // Validate active membership

        membershipService.requireActiveMembership(
                userId,
                LocalDate.now(clock)
        );

        // Lock book

        Book book = lockBook(bookId);

        if (book.isArchived()) {

            throw new NotFoundException(
                    "Book",
                    bookId
            );
        }

        // Refresh reservation queue

        refreshQueue(book);

        // Prevent reservation for already borrowed book

        if (loanRepository
                .existsByUser_IdAndCopy_Book_IdAndStatus(
                        userId,
                        bookId,
                        LoanStatus.ACTIVE
                )) {

            throw new ConflictException(
                    "ALREADY_BORROWED",
                    "You already borrowed this book"
            );
        }

        // Prevent duplicate reservations

        if (reservationRepository
                .findFirstByUser_IdAndBook_IdAndStatusIn(
                        userId,
                        bookId,
                        ACTIVE_STATUSES
                ).isPresent()) {

            throw new ConflictException(
                    "DUPLICATE_RESERVATION",
                    "You already reserved this book"
            );
        }

        // Check maximum reservation limit

        long total = reservationRepository
                .countByUser_IdAndStatusIn(
                        userId,
                        ACTIVE_STATUSES
                );

        if (total >= MAX_RESERVATIONS) {

            throw new BusinessRuleException(
                    "RESERVATION_LIMIT",
                    "Maximum " + MAX_RESERVATIONS
                            + " active reservations allowed"
            );
        }

        // Available books should be borrowed directly

        if (freeCopies(bookId) > 0) {

            throw new BusinessRuleException(
                    "BOOK_AVAILABLE",
                    "Book is available. Borrow it directly."
            );
        }

        // Create reservation

        Reservation reservation = new Reservation();

        reservation.setUser(user);

        reservation.setBook(book);

        reservation.setStatus(
                ReservationStatus.WAITING
        );

        reservation.setCreatedAt(
                LocalDateTime.now(clock)
        );

        reservationRepository.save(reservation);

        return toResponse(reservation);
    }

    // ==========================================
    // GET MEMBER RESERVATIONS
    // ==========================================

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations(
            Long userId
    ) {

        return reservationRepository
                .findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ==========================================
    // ADMIN: GET ALL RESERVATIONS
    // ==========================================

    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations() {

        return reservationRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ==========================================
    // CANCEL RESERVATION
    // ==========================================

    @Transactional
    public ReservationResponse cancel(
            Long reservationId,
            Long userId,
            boolean admin
    ) {

        Reservation reservation = reservationRepository
                .findById(reservationId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Reservation",
                                reservationId
                        )
                );

        // Lock associated book

        Book book = lockBook(
                reservation.getBook().getId()
        );

        // Refresh latest database state

        entityManager.refresh(reservation);

        // Member can cancel only their own reservation

        if (!admin
                && !reservation.getUser()
                .getId()
                .equals(userId)) {

            throw new AccessDeniedException(
                    "Not your reservation"
            );
        }

        // Only active reservations can be cancelled

        if (!ACTIVE_STATUSES.contains(
                reservation.getStatus()
        )) {

            throw new ConflictException(
                    "RESERVATION_CLOSED",
                    "Reservation is already closed"
            );
        }

        reservation.setStatus(
                ReservationStatus.CANCELLED
        );

        reservation.setCompletedAt(
                LocalDateTime.now(clock)
        );

        reservationRepository.save(reservation);

        // Give next waiting member priority

        refreshQueue(book);

        return toResponse(reservation);
    }

    // ==========================================
    // LOCK BOOK FOR CONCURRENT OPERATIONS
    // ==========================================

    @Transactional
    public Book lockBook(Long bookId) {

        Book book = entityManager.find(
                Book.class,
                bookId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (book == null) {

            throw new NotFoundException(
                    "Book",
                    bookId
            );
        }

        return book;
    }

    // ==========================================
    // CHECK BORROWING PRIORITY
    // ==========================================

    @Transactional
    public void checkBorrowAllowed(
            Long userId,
            Long bookId
    ) {

        Book book = lockBook(bookId);

        if (book.isArchived()) {

            throw new NotFoundException(
                    "Book",
                    bookId
            );
        }

        refreshQueue(book);

        // Check whether member has a READY reservation

        boolean hasReadyReservation =
                reservationRepository
                        .findFirstByUser_IdAndBook_IdAndStatusIn(
                                userId,
                                bookId,
                                List.of(
                                        ReservationStatus.READY
                                )
                        )
                        .isPresent();

        // Reserved member gets priority

        if (hasReadyReservation) {

            return;
        }

        // Other members can borrow only unreserved copies

        if (freeCopies(bookId) <= 0) {

            throw new ConflictException(
                    "BOOK_RESERVED",
                    "No unreserved copy is available"
            );
        }
    }

    // ==========================================
    // CHECK RENEWAL ALLOWED
    // ==========================================

    @Transactional
    public void checkRenewalAllowed(Long bookId) {

        Book book = lockBook(bookId);

        refreshQueue(book);

        // Check whether other members are waiting

        long waitingCount = reservationRepository
                .countByBook_IdAndStatus(
                        bookId,
                        ReservationStatus.WAITING
                );

        if (waitingCount > 0) {

            throw new BusinessRuleException(
                    "BOOK_HAS_RESERVATIONS",
                    "This book has pending reservations "
                            + "and cannot be renewed."
            );
        }
    }

    // ==========================================
    // COMPLETE RESERVATION AFTER BORROWING
    // ==========================================

    @Transactional
    public void completeReservation(
            Long userId,
            Long bookId
    ) {

        lockBook(bookId);

        reservationRepository
                .findFirstByUser_IdAndBook_IdAndStatusIn(
                        userId,
                        bookId,
                        List.of(
                                ReservationStatus.READY
                        )
                )
                .ifPresent(reservation -> {

                    reservation.setStatus(
                            ReservationStatus.FULFILLED
                    );

                    reservation.setCompletedAt(
                            LocalDateTime.now(clock)
                    );

                    reservationRepository.save(
                            reservation
                    );
                });
    }

    // ==========================================
    // WHEN BOOK BECOMES AVAILABLE
    // ==========================================

    @Transactional
    public void onBookAvailable(Long bookId) {

        Book book = lockBook(bookId);

        refreshQueue(book);
    }

    // ==========================================
    // EXPIRE OLD RESERVATIONS
    // ==========================================

    @Transactional
    public void expireReservations() {

        List<Long> bookIds =
                reservationRepository.findExpiredBookIds(
                        ReservationStatus.READY,
                        LocalDateTime.now(clock)
                );

        bookIds.stream()
                .sorted()
                .forEach(bookId -> {

                    Book book = lockBook(bookId);

                    refreshQueue(book);
                });
    }

    // ==========================================
    // FIFO RESERVATION QUEUE MANAGEMENT
    // ==========================================

    private void refreshQueue(Book book) {

        Long bookId = book.getId();

        LocalDateTime now = LocalDateTime.now(clock);

        // Fetch READY reservations

        List<Reservation> readyReservations =
                reservationRepository
                        .findByBook_IdAndStatusOrderByCreatedAtAscIdAsc(
                                bookId,
                                ReservationStatus.READY
                        );

        // Expire old reservations

        for (Reservation reservation : readyReservations) {

            if (reservation.getExpiresAt() != null
                    && !reservation.getExpiresAt()
                    .isAfter(now)) {

                reservation.setStatus(
                        ReservationStatus.EXPIRED
                );

                reservation.setCompletedAt(now);
            }
        }

        // Archived books cannot receive new allocations

        if (book.isArchived()) {

            return;
        }

        // Flush status changes before counting copies

        entityManager.flush();

        long free = freeCopies(bookId);

        if (free <= 0) {

            return;
        }

        // Fetch waiting members in FIFO order

        List<Reservation> waitingReservations =
                reservationRepository
                        .findByBook_IdAndStatusOrderByCreatedAtAscIdAsc(
                                bookId,
                                ReservationStatus.WAITING
                        );

        for (Reservation reservation : waitingReservations) {

            if (free <= 0) {

                break;
            }

            // Skip inactive members

            if (reservation.getUser().getStatus()
                    != UserStatus.ACTIVE) {

                reservation.setStatus(
                        ReservationStatus.CANCELLED
                );

                reservation.setCompletedAt(now);

                continue;
            }

            // Allocate available copy to waiting member

            reservation.setStatus(
                    ReservationStatus.READY
            );

            reservation.setReadyAt(now);

            reservation.setExpiresAt(
                    now.plusHours(HOLD_HOURS)
            );

            free--;

            // Publish reservation notification event

            eventPublisher.publishEvent(
                    new ReservationReadyEvent(
                            reservation.getId(),
                            reservation.getUser().getId(),
                            reservation.getUser().getEmail(),
                            reservation.getUser().getName(),
                            book.getTitle(),
                            reservation.getExpiresAt()
                    )
            );
        }
    }

    // ==========================================
    // COUNT AVAILABLE BOOK COPIES
    // ==========================================

    private long availableCopies(Long bookId) {

        return entityManager.createQuery(
                        """
                        SELECT COUNT(c)
                        FROM BookCopy c
                        WHERE c.book.id = :bookId
                        AND c.status = :status
                        """,
                        Long.class
                )
                .setParameter(
                        "bookId",
                        bookId
                )
                .setParameter(
                        "status",
                        CopyStatus.AVAILABLE
                )
                .getSingleResult();
    }

    // ==========================================
    // COUNT UNRESERVED AVAILABLE COPIES
    // ==========================================

    private long freeCopies(Long bookId) {

        long available = availableCopies(bookId);

        long reserved = reservationRepository
                .countByBook_IdAndStatus(
                        bookId,
                        ReservationStatus.READY
                );

        return Math.max(
                0,
                available - reserved
        );
    }

    // ==========================================
    // GET WAITING QUEUE POSITION
    // ==========================================

    private int queuePosition(
            Reservation reservation
    ) {

        if (reservation.getStatus()
                != ReservationStatus.WAITING) {

            return 0;
        }

        List<Reservation> queue =
                reservationRepository
                        .findByBook_IdAndStatusOrderByCreatedAtAscIdAsc(
                                reservation.getBook().getId(),
                                ReservationStatus.WAITING
                        );

        for (int i = 0; i < queue.size(); i++) {

            if (queue.get(i).getId().equals(
                    reservation.getId()
            )) {

                return i + 1;
            }
        }

        return 0;
    }

    // ==========================================
    // RESPONSE MAPPER
    // ==========================================

    private ReservationResponse toResponse(
            Reservation reservation
    ) {

        return new ReservationResponse(

                reservation.getId(),

                reservation.getBook().getId(),

                reservation.getBook().getTitle(),

                reservation.getBook().getAuthor(),

                reservation.getUser().getId(),

                reservation.getUser().getName(),

                reservation.getStatus(),

                queuePosition(reservation),

                reservation.getCreatedAt(),

                reservation.getReadyAt(),

                reservation.getExpiresAt()
        );
    }
}