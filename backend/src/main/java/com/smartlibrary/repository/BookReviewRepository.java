package com.smartlibrary.repository;

import com.smartlibrary.entity.BookReview;
import com.smartlibrary.entity.ReviewStatus;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BookReviewRepository
        extends JpaRepository<BookReview, Long> {

    // Public book reviews

    @EntityGraph(attributePaths = {"user", "book"})
    Page<BookReview>
    findByBook_IdAndStatusOrderByCreatedAtDescIdDesc(
            Long bookId,
            ReviewStatus status,
            Pageable pageable
    );

    // Member's own reviews

    @EntityGraph(attributePaths = {"user", "book"})
    Page<BookReview>
    findByUser_IdOrderByCreatedAtDescIdDesc(
            Long userId,
            Pageable pageable
    );

    // Find member review for specific book

    @EntityGraph(attributePaths = {"user", "book"})
    Optional<BookReview> findByUser_IdAndBook_Id(
            Long userId,
            Long bookId
    );

    // Duplicate review check

    boolean existsByUser_IdAndBook_Id(
            Long userId,
            Long bookId
    );

    // Admin review list

    @EntityGraph(attributePaths = {"user", "book"})
    Page<BookReview> findAllByOrderByCreatedAtDescIdDesc(
            Pageable pageable
    );

    // Admin review filter

    @EntityGraph(attributePaths = {"user", "book"})
    Page<BookReview>
    findByStatusOrderByCreatedAtDescIdDesc(
            ReviewStatus status,
            Pageable pageable
    );

    // Lock review before update or delete

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from BookReview r
            where r.id = :id
            """)
    Optional<BookReview> findByIdForUpdate(
            @Param("id") Long id
    );

    // Rating distribution

    @Query("""
            select r.rating, count(r)
            from BookReview r
            where r.book.id = :bookId
              and r.status = :status
            group by r.rating
            order by r.rating desc
            """)
    List<Object[]> ratingDistribution(
            @Param("bookId") Long bookId,
            @Param("status") ReviewStatus status
    );
}