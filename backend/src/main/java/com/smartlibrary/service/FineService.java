package com.smartlibrary.service;

import com.smartlibrary.config.FineProperties;

import com.smartlibrary.dto.FineDto.FineResponse;
import com.smartlibrary.dto.FineDto.FineSummaryResponse;
import com.smartlibrary.dto.FineDto.FineEstimateResponse;
import com.smartlibrary.dto.LoanDto.ReturnCondition;

import com.smartlibrary.entity.Fine;
import com.smartlibrary.entity.FineType;
import com.smartlibrary.entity.FineStatus;
import com.smartlibrary.entity.Loan;
import com.smartlibrary.entity.LoanStatus;

import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.ConflictException;
import com.smartlibrary.exception.NotFoundException;

import com.smartlibrary.repository.FineRepository;
import com.smartlibrary.repository.LoanRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FineService {

    private final FineRepository fineRepository;

    private final LoanRepository loanRepository;

    private final FineProperties properties;

    private final AuditService auditService;

    private final Clock clock;

    private static final BigDecimal ZERO =
            new BigDecimal("0.00");

    // ==========================================
    // CALCULATE OVERDUE DAYS
    // ==========================================

    private long calculateOverdueDays(
            Loan loan,
            LocalDate date
    ) {

        return Math.max(
                0,
                ChronoUnit.DAYS.between(
                        loan.getDueDate(),
                        date
                )
        );
    }

    // ==========================================
    // CALCULATE OVERDUE AMOUNT
    // ==========================================

    private BigDecimal calculateOverdueAmount(
            long overdueDays
    ) {

        return properties.getOverduePerDay()

                .multiply(
                        BigDecimal.valueOf(overdueDays)
                )

                .min(
                        properties.getMaxOverdueFine()
                )

                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    // ==========================================
    // CREATE FINES ON BOOK RETURN
    // ==========================================

    @Transactional
    public void createFinesForReturn(
            Loan loan,
            ReturnCondition condition
    ) {

        LocalDate today = LocalDate.now(clock);

        long overdueDays = calculateOverdueDays(
                loan,
                today
        );

        BigDecimal overdueAmount =
                calculateOverdueAmount(overdueDays);

        // Late return fine

        if (overdueAmount.signum() > 0) {

            createFine(

                    loan,

                    FineType.OVERDUE,

                    overdueAmount,

                    overdueDays,

                    "Book overdue by "
                            + overdueDays
                            + " day(s)"
            );
        }

        // Damaged book fine

        if (condition == ReturnCondition.DAMAGED) {

            createFine(

                    loan,

                    FineType.DAMAGED,

                    properties.getDamagedCharge(),

                    0,

                    "Book returned in damaged condition"
            );
        }

        // Lost book fine

        if (condition == ReturnCondition.LOST) {

            createFine(

                    loan,

                    FineType.LOST,

                    properties.getLostCharge(),

                    0,

                    "Borrowed book marked as lost"
            );
        }
    }

    // ==========================================
    // CREATE INDIVIDUAL FINE
    // ==========================================

    private void createFine(

            Loan loan,

            FineType type,

            BigDecimal amount,

            long overdueDays,

            String description

    ) {

        if (amount.signum() <= 0) {

            return;
        }

        // Prevent duplicate fine for same loan/type

        if (fineRepository.existsByLoan_IdAndType(
                loan.getId(),
                type
        )) {

            return;
        }

        Fine fine = new Fine();

        fine.setUser(
                loan.getUser()
        );

        fine.setLoan(loan);

        fine.setType(type);

        fine.setStatus(
                FineStatus.PENDING
        );

        fine.setAmount(
                amount.setScale(
                        2,
                        RoundingMode.HALF_UP
                )
        );

        fine.setOverdueDays(
                overdueDays
        );

        fine.setDescription(
                description
        );

        fine.setCreatedAt(
                LocalDateTime.now(clock)
        );

        fineRepository.save(fine);
    }

    // ==========================================
    // MEMBER: GET ALL FINES
    // ==========================================

    @Transactional(readOnly = true)
    public List<FineResponse> getMyFines(
            Long userId
    ) {

        return fineRepository

                .findByUser_IdOrderByCreatedAtDesc(
                        userId
                )

                .stream()

                .map(this::toResponse)

                .toList();
    }

    // ==========================================
    // MEMBER: FINE SUMMARY
    // ==========================================

    @Transactional(readOnly = true)
    public FineSummaryResponse getMySummary(
            Long userId
    ) {

        List<Fine> fines = fineRepository
                .findByUser_IdOrderByCreatedAtDesc(
                        userId
                );

        return buildSummary(fines);
    }

    // ==========================================
    // ADMIN: GET ALL FINES
    // ==========================================

    @Transactional(readOnly = true)
    public List<FineResponse> getAllFines(
            FineStatus status
    ) {

        List<Fine> fines;

        if (status == null) {

            fines = fineRepository
                    .findAllByOrderByCreatedAtDesc();

        } else {

            fines = fineRepository
                    .findByStatusOrderByCreatedAtDesc(
                            status
                    );
        }

        return fines.stream()
                .map(this::toResponse)
                .toList();
    }

    // ==========================================
    // ADMIN: FINE DASHBOARD SUMMARY
    // ==========================================

    @Transactional(readOnly = true)
    public FineSummaryResponse getAdminSummary() {

        return buildSummary(

                fineRepository
                        .findAllByOrderByCreatedAtDesc()
        );
    }

    // ==========================================
    // ADMIN: RECORD CASH PAYMENT
    // ==========================================

    @Transactional
    public FineResponse markPaid(

            Long fineId,

            Long adminId

    ) {

        Fine fine = lockFine(fineId);

        requirePending(fine);

        fine.setStatus(
                FineStatus.PAID
        );

        fine.setPaymentMethod(
                "CASH"
        );

        fine.setPaidAt(
                LocalDateTime.now(clock)
        );

        fine.setProcessedByAdminId(
                adminId
        );

        fineRepository.save(fine);

        auditService.log(

                adminId,

                "FINE_PAID",

                "FINE",

                fine.getId(),

                "Cash payment recorded. Amount="
                        + fine.getAmount()
        );

        return toResponse(fine);
    }

    // ==========================================
    // ADMIN: WAIVE FINE
    // ==========================================

    @Transactional
    public FineResponse waiveFine(

            Long fineId,

            Long adminId,

            String reason

    ) {

        Fine fine = lockFine(fineId);

        requirePending(fine);

        if (reason == null
                || reason.isBlank()
                || reason.length() > 500) {

            throw new BusinessRuleException(

                    "INVALID_WAIVER_REASON",

                    "A valid waiver reason is required"
            );
        }

        fine.setStatus(
                FineStatus.WAIVED
        );

        fine.setWaiverReason(
                reason.trim()
        );

        fine.setWaivedAt(
                LocalDateTime.now(clock)
        );

        fine.setProcessedByAdminId(
                adminId
        );

        fineRepository.save(fine);

        auditService.log(

                adminId,

                "FINE_WAIVED",

                "FINE",

                fine.getId(),

                "Fine waived. Reason=" + reason
        );

        return toResponse(fine);
    }

    // ==========================================
    // MEMBER: CURRENT OVERDUE FINE ESTIMATE
    // ==========================================

    @Transactional(readOnly = true)
    public FineEstimateResponse estimateFine(

            Long loanId,

            Long userId,

            boolean admin,

            ReturnCondition condition

    ) {

        Loan loan = loanRepository
                .findById(loanId)

                .orElseThrow(
                        () -> new NotFoundException(
                                "Loan",
                                loanId
                        )
                );

        if (!admin
                && !loan.getUser()
                .getId()
                .equals(userId)) {

            throw new AccessDeniedException(
                    "Not your loan"
            );
        }

        if (loan.getStatus() != LoanStatus.ACTIVE) {

            throw new BusinessRuleException(

                    "LOAN_NOT_ACTIVE",

                    "Fine estimate is available "
                            + "only for active loans"
            );
        }

        long overdueDays = calculateOverdueDays(

                loan,

                LocalDate.now(clock)
        );

        BigDecimal overdueFine =
                calculateOverdueAmount(
                        overdueDays
                );

        BigDecimal damageCharge =
                condition == ReturnCondition.DAMAGED

                        ? properties.getDamagedCharge()

                        : ZERO;

        BigDecimal lostCharge =
                condition == ReturnCondition.LOST

                        ? properties.getLostCharge()

                        : ZERO;

        BigDecimal total = overdueFine

                .add(damageCharge)

                .add(lostCharge);

        return new FineEstimateResponse(

                loanId,

                overdueDays,

                overdueFine,

                damageCharge,

                lostCharge,

                total
        );
    }

    // ==========================================
    // BLOCK BORROWING ON PENDING FINES
    // ==========================================

    @Transactional(readOnly = true)
    public void requireNoPendingFine(
            Long userId
    ) {

        if (!properties
                .isBlockBorrowingOnPendingFine()) {

            return;
        }

        boolean hasPendingFine =
                fineRepository
                        .existsByUser_IdAndStatus(

                                userId,

                                FineStatus.PENDING
                        );

        if (hasPendingFine) {

            throw new BusinessRuleException(

                    "PENDING_FINE_BLOCK",

                    "Please clear your pending "
                            + "library fines before "
                            + "borrowing another book."
            );
        }
    }

    // ==========================================
    // LOCK FINE
    // ==========================================

    private Fine lockFine(Long fineId) {

        return fineRepository
                .findByIdForUpdate(fineId)

                .orElseThrow(
                        () -> new NotFoundException(
                                "Fine",
                                fineId
                        )
                );
    }

    // ==========================================
    // VALIDATE FINE STATUS
    // ==========================================

    private void requirePending(Fine fine) {

        if (fine.getStatus()
                != FineStatus.PENDING) {

            throw new ConflictException(

                    "FINE_ALREADY_CLOSED",

                    "This fine is already paid or waived"
            );
        }
    }

    // ==========================================
    // BUILD FINE SUMMARY
    // ==========================================

    private FineSummaryResponse buildSummary(
            List<Fine> fines
    ) {

        long pending = fines.stream()

                .filter(f ->
                        f.getStatus()
                                == FineStatus.PENDING
                )

                .count();

        long paid = fines.stream()

                .filter(f ->
                        f.getStatus()
                                == FineStatus.PAID
                )

                .count();

        long waived = fines.stream()

                .filter(f ->
                        f.getStatus()
                                == FineStatus.WAIVED
                )

                .count();

        BigDecimal pendingAmount =
                sumByStatus(
                        fines,
                        FineStatus.PENDING
                );

        BigDecimal paidAmount =
                sumByStatus(
                        fines,
                        FineStatus.PAID
                );

        BigDecimal waivedAmount =
                sumByStatus(
                        fines,
                        FineStatus.WAIVED
                );

        BigDecimal totalAmount = pendingAmount

                .add(paidAmount)

                .add(waivedAmount);

        return new FineSummaryResponse(

                fines.size(),

                pending,

                paid,

                waived,

                totalAmount,

                pendingAmount,

                paidAmount,

                waivedAmount,

                fines.stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    // ==========================================
    // SUM FINES BY STATUS
    // ==========================================

    private BigDecimal sumByStatus(

            List<Fine> fines,

            FineStatus status

    ) {

        return fines.stream()

                .filter(f ->
                        f.getStatus() == status
                )

                .map(Fine::getAmount)

                .reduce(
                        ZERO,
                        BigDecimal::add
                );
    }

    // ==========================================
    // RESPONSE MAPPER
    // ==========================================

    private FineResponse toResponse(Fine fine) {

        Loan loan = fine.getLoan();

        return new FineResponse(

                fine.getId(),

                fine.getUser().getId(),

                fine.getUser().getName(),

                loan.getId(),

                loan.getCopy().getBook().getId(),

                loan.getCopy().getBook().getTitle(),

                fine.getType(),

                fine.getStatus(),

                fine.getAmount(),

                fine.getOverdueDays(),

                fine.getDescription(),

                fine.getPaymentMethod(),

                fine.getWaiverReason(),

                fine.getCreatedAt(),

                fine.getPaidAt(),

                fine.getWaivedAt()
        );
    }
}