package com.smartlibrary.repository;

import com.smartlibrary.dto.projection.CategoryCount;
import com.smartlibrary.dto.projection.MonthlyTrend;

import com.smartlibrary.entity.Loan;
import com.smartlibrary.entity.LoanStatus;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface LoanRepository
        extends JpaRepository<Loan, Long>,
        JpaSpecificationExecutor<Loan> {

    // ==========================================
    // EXISTING LOAN METHODS
    // ==========================================

    @Override
    @EntityGraph(
            attributePaths = {
                    "user",
                    "copy",
                    "copy.book"
            }
    )
    Page<Loan> findAll(
            Specification<Loan> spec,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l
            from Loan l
            where l.id = :id
            """)
    Optional<Loan> findByIdForUpdate(
            @Param("id") Long id
    );

    boolean existsByUser_IdAndCopy_Book_IdAndStatus(
            Long userId,
            Long bookId,
            LoanStatus status
    );

    boolean existsByCopy_Book_IdAndStatus(
            Long bookId,
            LoanStatus status
    );

    long countByUser_IdAndStatus(
            Long userId,
            LoanStatus status
    );

    boolean existsByUser_IdAndStatusAndDueDateBefore(
            Long userId,
            LoanStatus status,
            LocalDate date
    );

    long countByStatus(
            LoanStatus status
    );

    long countByStatusAndDueDateBefore(
            LoanStatus status,
            LocalDate date
    );

    // ==========================================
    // EMAIL REMINDER SYSTEM
    // ==========================================

    @EntityGraph(
            attributePaths = {
                    "user",
                    "copy",
                    "copy.book"
            }
    )
    List<Loan> findByStatus(
            LoanStatus status
    );

    // ==========================================
    // BOOK REVIEW VERIFICATION
    // ==========================================

    boolean existsByUser_IdAndCopy_Book_Id(
            Long userId,
            Long bookId
    );

    // ==========================================
    // ADMIN CATEGORY REPORT
    // ==========================================

    @Query("""
            select new com.smartlibrary.dto.projection.CategoryCount(
                c.name,
                count(l)
            )
            from Loan l
            join l.copy cp
            join cp.book b
            join b.category c
            group by c.name
            order by count(l) desc
            """)
    List<CategoryCount> countLoansByCategory();

    // ==========================================
    // ADMIN MONTHLY REPORT
    // ==========================================

    @Query("""
            select new com.smartlibrary.dto.projection.MonthlyTrend(
                year(l.issueDate),
                month(l.issueDate),
                count(l)
            )
            from Loan l
            where l.issueDate >= :from
            group by year(l.issueDate), month(l.issueDate)
            order by year(l.issueDate), month(l.issueDate)
            """)
    List<MonthlyTrend> monthlyTrend(
            @Param("from") LocalDate from
    );

    // ==========================================
    // MEMBER ANALYTICS: TOTAL BORROWED
    // ==========================================

    long countByUser_Id(
            Long userId
    );

    // ==========================================
    // MEMBER ANALYTICS: UNIQUE BOOKS
    // ==========================================

    @Query("""
            select count(distinct l.copy.book.id)
            from Loan l
            where l.user.id = :userId
            """)
    long countUniqueBooks(
            @Param("userId") Long userId
    );

    // ==========================================
    // MEMBER ANALYTICS: OVERDUE BOOKS
    // ==========================================

    long countByUser_IdAndStatusAndDueDateBefore(

            Long userId,

            LoanStatus status,

            LocalDate date
    );

    // ==========================================
    // MEMBER ANALYTICS: DUE SOON
    // ==========================================

    long countByUser_IdAndStatusAndDueDateBetween(

            Long userId,

            LoanStatus status,

            LocalDate from,

            LocalDate to
    );

    // ==========================================
    // MEMBER MONTHLY BORROWING ACTIVITY
    // ==========================================

    @Query("""
            select
                year(l.issueDate),
                month(l.issueDate),
                count(l)
            from Loan l
            where l.user.id = :userId
              and l.issueDate >= :from
              and l.issueDate <= :to
            group by
                year(l.issueDate),
                month(l.issueDate)
            order by
                year(l.issueDate),
                month(l.issueDate)
            """)
    List<Object[]> memberMonthlyActivity(

            @Param("userId") Long userId,

            @Param("from") LocalDate from,

            @Param("to") LocalDate to
    );

    // ==========================================
    // MEMBER FAVOURITE CATEGORIES
    // ==========================================

    @Query("""
            select
                c.id,
                c.name,
                count(l)
            from Loan l
            join l.copy cp
            join cp.book b
            join b.category c
            where l.user.id = :userId
            group by c.id, c.name
            order by count(l) desc, c.id asc
            """)
    List<Object[]> memberCategoryActivity(
            @Param("userId") Long userId
    );

    // ==========================================
    // RECENT MEMBER LOANS
    // ==========================================

    @EntityGraph(
            attributePaths = {
                    "copy",
                    "copy.book"
            }
    )
    List<Loan>
    findByUser_IdOrderByIssueDateDescIdDesc(

            Long userId,

            Pageable pageable
    );

    // ==========================================
    // UPCOMING RETURNS
    // ==========================================

    @EntityGraph(
            attributePaths = {
                    "copy",
                    "copy.book"
            }
    )
    List<Loan>
    findByUser_IdAndStatusOrderByDueDateAscIdAsc(

            Long userId,

            LoanStatus status,

            Pageable pageable
    );
}