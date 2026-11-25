package com.smartlibrary.service;

import com.smartlibrary.dto.MemberAnalyticsDto.*;

import com.smartlibrary.entity.*;

import com.smartlibrary.repository.*;

import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.NotFoundException;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;

import java.util.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAnalyticsService {

    private final LoanRepository loanRepository;

    private final BookRepository bookRepository;

    private final UserRepository userRepository;

    private final WishlistRepository wishlistRepository;

    private final ReservationRepository reservationRepository;

    private final FineRepository fineRepository;

    private final DemoPaymentRepository paymentRepository;

    private final MembershipService membershipService;

    private final EntityManager entityManager;

    private final Clock clock;

    private static final int RECENT_LIMIT = 5;

    private static final int RECOMMENDATION_LIMIT = 6;

    private static final int DUE_SOON_DAYS = 3;

    // ==========================================
    // COMPLETE MEMBER DASHBOARD
    // ==========================================

    public DashboardResponse getDashboard(
            Long userId
    ) {

        User user = getMember(userId);

        return new DashboardResponse(

                user.getId(),

                user.getName(),

                membershipService.currentFor(userId),

                getReadingStats(userId),

                getAccountSummary(userId),

                getMonthlyActivity(userId, 6),

                getFavouriteCategories(userId),

                getRecentActivity(userId),

                getUpcomingReturns(userId),

                getRecommendedBooks(userId)
        );
    }

    // ==========================================
    // MEMBER READING STATISTICS
    // ==========================================

    public ReadingStats getReadingStats(
            Long userId
    ) {

        getMember(userId);

        LocalDate today = LocalDate.now(clock);

        long totalBorrowed =
                loanRepository.countByUser_Id(userId);

        long uniqueBooks =
                loanRepository.countUniqueBooks(userId);

        long returnedBooks =
                loanRepository.countByUser_IdAndStatus(

                        userId,

                        LoanStatus.RETURNED
                );

        long activeLoans =
                loanRepository.countByUser_IdAndStatus(

                        userId,

                        LoanStatus.ACTIVE
                );

        long lostBooks =
                loanRepository.countByUser_IdAndStatus(

                        userId,

                        LoanStatus.LOST
                );

        long overdueBooks =
                loanRepository
                        .countByUser_IdAndStatusAndDueDateBefore(

                                userId,

                                LoanStatus.ACTIVE,

                                today
                        );

        long dueSoonBooks =
                loanRepository
                        .countByUser_IdAndStatusAndDueDateBetween(

                                userId,

                                LoanStatus.ACTIVE,

                                today,

                                today.plusDays(DUE_SOON_DAYS)
                        );

        double returnRate =
                totalBorrowed == 0

                        ? 0.0

                        : roundOneDecimal(

                        returnedBooks * 100.0
                                / totalBorrowed
                );

        return new ReadingStats(

                totalBorrowed,

                uniqueBooks,

                returnedBooks,

                activeLoans,

                overdueBooks,

                lostBooks,

                dueSoonBooks,

                returnRate
        );
    }

    // ==========================================
    // MONTHLY BORROWING GRAPH
    // ==========================================

    public List<MonthlyActivity> getMonthlyActivity(

            Long userId,

            int months

    ) {

        getMember(userId);

        if (months < 1 || months > 24) {

            throw new BusinessRuleException(

                    "INVALID_MONTH_RANGE",

                    "Months must be between 1 and 24"
            );
        }

        LocalDate today = LocalDate.now(clock);

        YearMonth currentMonth =
                YearMonth.from(today);

        YearMonth firstMonth =
                currentMonth.minusMonths(months - 1L);

        List<Object[]> rows =
                loanRepository.memberMonthlyActivity(

                        userId,

                        firstMonth.atDay(1),

                        today
                );

        Map<YearMonth, Long> monthlyMap =
                new HashMap<>();

        for (Object[] row : rows) {

            int year =
                    ((Number) row[0]).intValue();

            int month =
                    ((Number) row[1]).intValue();

            long count =
                    ((Number) row[2]).longValue();

            monthlyMap.put(

                    YearMonth.of(year, month),

                    count
            );
        }

        List<MonthlyActivity> response =
                new ArrayList<>();

        for (int i = 0; i < months; i++) {

            YearMonth month =
                    firstMonth.plusMonths(i);

            String monthName =
                    month.getMonth().getDisplayName(

                            TextStyle.SHORT,

                            Locale.ENGLISH
                    );

            response.add(

                    new MonthlyActivity(

                            month.getYear(),

                            month.getMonthValue(),

                            monthName,

                            monthlyMap.getOrDefault(

                                    month,

                                    0L
                            )
                    )
            );
        }

        return response;
    }

    // ==========================================
    // FAVOURITE BOOK CATEGORIES
    // ==========================================

    public List<CategoryActivity> getFavouriteCategories(
            Long userId
    ) {

        getMember(userId);

        List<Object[]> rows =
                loanRepository.memberCategoryActivity(userId);

        long total = rows.stream()

                .mapToLong(

                        row -> ((Number) row[2]).longValue()
                )

                .sum();

        List<CategoryActivity> categories =
                new ArrayList<>();

        for (Object[] row : rows) {

            Long categoryId =
                    ((Number) row[0]).longValue();

            String categoryName =
                    (String) row[1];

            long count =
                    ((Number) row[2]).longValue();

            double percentage =
                    total == 0

                            ? 0.0

                            : roundOneDecimal(

                            count * 100.0 / total
                    );

            categories.add(

                    new CategoryActivity(

                            categoryId,

                            categoryName,

                            count,

                            percentage
                    )
            );
        }

        return categories;
    }

    // ==========================================
    // RECENT READING ACTIVITY
    // ==========================================

    public List<RecentActivity> getRecentActivity(
            Long userId
    ) {

        getMember(userId);

        LocalDate today = LocalDate.now(clock);

        return loanRepository

                .findByUser_IdOrderByIssueDateDescIdDesc(

                        userId,

                        PageRequest.of(0, RECENT_LIMIT)
                )

                .stream()

                .map(loan -> {

                    Book book =
                            loan.getCopy().getBook();

                    boolean overdue =

                            loan.getStatus() == LoanStatus.ACTIVE

                                    && loan.getDueDate()
                                    .isBefore(today);

                    return new RecentActivity(

                            loan.getId(),

                            book.getId(),

                            book.getTitle(),

                            book.getAuthor(),

                            loan.getIssueDate(),

                            loan.getDueDate(),

                            loan.getReturnedAt(),

                            loan.getStatus().name(),

                            overdue
                    );
                })

                .toList();
    }

    // ==========================================
    // UPCOMING RETURNS
    // ==========================================

    public List<UpcomingReturn> getUpcomingReturns(
            Long userId
    ) {

        getMember(userId);

        LocalDate today = LocalDate.now(clock);

        return loanRepository

                .findByUser_IdAndStatusOrderByDueDateAscIdAsc(

                        userId,

                        LoanStatus.ACTIVE,

                        PageRequest.of(0, 10)
                )

                .stream()

                .map(loan -> {

                    Book book =
                            loan.getCopy().getBook();

                    long daysRemaining =
                            ChronoUnit.DAYS.between(

                                    today,

                                    loan.getDueDate()
                            );

                    return new UpcomingReturn(

                            loan.getId(),

                            book.getId(),

                            book.getTitle(),

                            book.getAuthor(),

                            loan.getDueDate(),

                            daysRemaining,

                            daysRemaining < 0
                    );
                })

                .toList();
    }

    // ==========================================
    // MEMBER ACCOUNT SUMMARY
    // ==========================================

    public AccountSummary getAccountSummary(
            Long userId
    ) {

        getMember(userId);

        long wishlistCount =
                entityManager.createQuery("""
                        select count(w)
                        from Wishlist w
                        where w.user.id = :userId
                          and w.book.archived = false
                        """, Long.class)

                        .setParameter("userId", userId)

                        .getSingleResult();

        long waitingReservations =
                reservationRepository.countByUser_IdAndStatusIn(

                        userId,

                        List.of(ReservationStatus.WAITING)
                );

        long readyReservations =
                reservationRepository.countByUser_IdAndStatusIn(

                        userId,

                        List.of(ReservationStatus.READY)
                );

        Object[] fineSummary =
                entityManager.createQuery("""
                        select count(f), sum(f.amount)
                        from Fine f
                        where f.user.id = :userId
                          and f.status = :status
                        """, Object[].class)

                        .setParameter("userId", userId)

                        .setParameter(
                                "status",
                                FineStatus.PENDING
                        )

                        .getSingleResult();

        long pendingFineCount =
                ((Number) fineSummary[0]).longValue();

        BigDecimal pendingFineAmount =

                fineSummary[1] == null

                        ? BigDecimal.ZERO

                        : (BigDecimal) fineSummary[1];

        long paymentCount =
                paymentRepository.countByUserId(userId);

        return new AccountSummary(

                wishlistCount,

                waitingReservations,

                readyReservations,

                pendingFineCount,

                pendingFineAmount,

                paymentCount
        );
    }

    // ==========================================
    // PERSONALIZED BOOK RECOMMENDATIONS
    // ==========================================

    public List<RecommendedBook> getRecommendedBooks(
            Long userId
    ) {

        getMember(userId);

        List<Long> categoryIds =
                getFavouriteCategories(userId)

                        .stream()

                        .limit(3)

                        .map(CategoryActivity::categoryId)

                        .toList();

        Map<Long, RecommendedBook> recommendations =
                new LinkedHashMap<>();

        // Recommend books from favourite categories.

        if (!categoryIds.isEmpty()) {

            List<Book> personalizedBooks =
                    bookRepository.findPersonalizedRecommendations(

                            userId,

                            categoryIds,

                            CopyStatus.AVAILABLE,

                            PageRequest.of(
                                    0,
                                    RECOMMENDATION_LIMIT
                            )
                    );

            for (Book book : personalizedBooks) {

                recommendations.put(

                        book.getId(),

                        toRecommendedBook(

                                book,

                                "Based on your borrowing interests"
                        )
                );
            }
        }

        // Fill remaining slots with general books.

        if (recommendations.size() < RECOMMENDATION_LIMIT) {

            List<Book> generalBooks =
                    bookRepository.findGeneralRecommendations(

                            userId,

                            CopyStatus.AVAILABLE,

                            PageRequest.of(
                                    0,
                                    RECOMMENDATION_LIMIT * 2
                            )
                    );

            for (Book book : generalBooks) {

                if (recommendations.size()
                        >= RECOMMENDATION_LIMIT) {

                    break;
                }

                if (!recommendations.containsKey(book.getId())) {

                    recommendations.put(

                            book.getId(),

                            toRecommendedBook(

                                    book,

                                    "Discover more library books"
                            )
                    );
                }
            }
        }

        return new ArrayList<>(
                recommendations.values()
        );
    }

    // ==========================================
    // MAP RECOMMENDED BOOK
    // ==========================================

    private RecommendedBook toRecommendedBook(

            Book book,

            String reason

    ) {

        long availableCopies =
                entityManager.createQuery("""
                        select count(c)
                        from BookCopy c
                        where c.book.id = :bookId
                          and c.status = :status
                        """, Long.class)

                        .setParameter(
                                "bookId",
                                book.getId()
                        )

                        .setParameter(
                                "status",
                                CopyStatus.AVAILABLE
                        )

                        .getSingleResult();

        String categoryName =

                book.getCategory() == null

                        ? "Uncategorized"

                        : book.getCategory().getName();

        return new RecommendedBook(

                book.getId(),

                book.getTitle(),

                book.getAuthor(),

                book.getIsbn(),

                categoryName,

                availableCopies,

                reason
        );
    }

    // ==========================================
    // VALIDATE MEMBER
    // ==========================================

    private User getMember(Long userId) {

        User user = userRepository.findById(userId)

                .orElseThrow(

                        () -> new NotFoundException(

                                "User",

                                userId
                        )
                );

        if (user.getRole() != Role.MEMBER

                || user.getStatus() != UserStatus.ACTIVE) {

            throw new BusinessRuleException(

                    "INVALID_MEMBER",

                    "Active member required"
            );
        }

        return user;
    }

    // ==========================================
    // ROUND PERCENTAGE
    // ==========================================

    private double roundOneDecimal(double value) {

        return Math.round(value * 10.0) / 10.0;
    }
}