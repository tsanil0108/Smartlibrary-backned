package com.smartlibrary.dto;

import com.smartlibrary.dto.MembershipDto.MembershipResponse;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.List;

public final class MemberAnalyticsDto {

    private MemberAnalyticsDto() {
    }

    // ==========================================
    // MEMBER READING STATISTICS
    // ==========================================

    public record ReadingStats(

            long totalBorrowed,

            long uniqueBooksBorrowed,

            long returnedBooks,

            long activeLoans,

            long overdueBooks,

            long lostBooks,

            long dueSoonBooks,

            double returnRate

    ) {
    }

    // ==========================================
    // MONTHLY BORROWING ACTIVITY
    // ==========================================

    public record MonthlyActivity(

            int year,

            int month,

            String monthName,

            long borrowedBooks

    ) {
    }

    // ==========================================
    // FAVOURITE BOOK CATEGORIES
    // ==========================================

    public record CategoryActivity(

            Long categoryId,

            String categoryName,

            long borrowedBooks,

            double percentage

    ) {
    }

    // ==========================================
    // RECENT LOAN ACTIVITY
    // ==========================================

    public record RecentActivity(

            Long loanId,

            Long bookId,

            String bookTitle,

            String author,

            LocalDate issueDate,

            LocalDate dueDate,

            LocalDateTime returnedAt,

            String status,

            boolean overdue

    ) {
    }

    // ==========================================
    // UPCOMING BOOK RETURNS
    // ==========================================

    public record UpcomingReturn(

            Long loanId,

            Long bookId,

            String bookTitle,

            String author,

            LocalDate dueDate,

            long daysRemaining,

            boolean overdue

    ) {
    }

    // ==========================================
    // RECOMMENDED BOOK
    // ==========================================

    public record RecommendedBook(

            Long bookId,

            String title,

            String author,

            String isbn,

            String categoryName,

            long availableCopies,

            String recommendationReason

    ) {
    }

    // ==========================================
    // MEMBER ACCOUNT SUMMARY
    // ==========================================

    public record AccountSummary(

            long wishlistCount,

            long waitingReservations,

            long readyReservations,

            long pendingFineCount,

            BigDecimal pendingFineAmount,

            long paymentCount

    ) {
    }

    // ==========================================
    // COMPLETE MEMBER DASHBOARD
    // ==========================================

    public record DashboardResponse(

            Long userId,

            String memberName,

            MembershipResponse membership,

            ReadingStats readingStats,

            AccountSummary accountSummary,

            List<MonthlyActivity> monthlyActivity,

            List<CategoryActivity> favouriteCategories,

            List<RecentActivity> recentActivity,

            List<UpcomingReturn> upcomingReturns,

            List<RecommendedBook> recommendedBooks

    ) {
    }
}