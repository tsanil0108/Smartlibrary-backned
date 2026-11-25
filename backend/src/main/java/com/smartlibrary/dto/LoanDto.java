package com.smartlibrary.dto;

import com.smartlibrary.entity.Loan;
import com.smartlibrary.entity.LoanStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class LoanDto {
    private LoanDto() {}


    public enum ReturnCondition { GOOD, DAMAGED, LOST }

    public record BorrowRequest(@NotNull Long bookId) {}

    public record AdminIssueRequest(@NotNull Long userId, @NotNull Long bookId) {}

    public record ReturnRequest(ReturnCondition condition) {}

    public record LoanResponse(Long id, Long userId, String userName, Long bookId, String bookTitle, Long copyId,
                               String accessionNo, LocalDate issueDate, LocalDate dueDate, LocalDateTime returnedAt,
                               LoanStatus status, boolean overdue, long daysOverdue, int renewCount) {

        public static LoanResponse from(Loan l, LocalDate today) {
            boolean overdue = l.getStatus() == LoanStatus.ACTIVE && l.getDueDate().isBefore(today);
            long days = overdue ? ChronoUnit.DAYS.between(l.getDueDate(), today) : 0;
            return new LoanResponse(l.getId(), l.getUser().getId(), l.getUser().getName(),
                    l.getCopy().getBook().getId(), l.getCopy().getBook().getTitle(),
                    l.getCopy().getId(), l.getCopy().getAccessionNo(),
                    l.getIssueDate(), l.getDueDate(), l.getReturnedAt(), l.getStatus(), overdue, days, l.getRenewCount());
        }
    }
}
