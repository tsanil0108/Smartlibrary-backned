package com.smartlibrary.repository;

import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.CopyStatus;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository
        extends JpaRepository<Book, Long> {

    // ==========================================
    // CHECK DUPLICATE ISBN
    // ==========================================

    boolean existsByIsbn(String isbn);

    // ==========================================
    // CHECK DUPLICATE ISBN DURING UPDATE
    // ==========================================

    boolean existsByIsbnAndIdNot(
            String isbn,
            Long id
    );

    // ==========================================
    // COUNT ACTIVE BOOKS
    // ==========================================

    long countByArchivedFalse();

    // ==========================================
    // PUBLIC BOOK CATALOGUE SEARCH
    // ==========================================

    @EntityGraph(attributePaths = "category")
    @Query("""
            select b from Book b
            where b.archived = false

              and (
                    lower(b.title) like :q
                    or lower(b.author) like :q
                    or lower(coalesce(b.isbn, '')) like :q
              )

              and lower(b.author) like :author

              and (
                    :category = ''
                    or lower(b.category.name) = :category
              )

              and (
                    :onlyAvailable = false
                    or exists (
                        select 1
                        from BookCopy c
                        where c.book = b
                          and c.status =
                              com.smartlibrary.entity.CopyStatus.AVAILABLE
                    )
              )
            """)
    Page<Book> search(

            @Param("q")
            String q,

            @Param("author")
            String author,

            @Param("category")
            String category,

            @Param("onlyAvailable")
            boolean onlyAvailable,

            Pageable pageable
    );

    // ==========================================
    // PERSONALIZED BOOK RECOMMENDATIONS
    // ==========================================

    /*
     * Used by MemberAnalyticsService.
     *
     * Recommends books based on the member's
     * favourite borrowing categories.
     *
     * Rules:
     *
     * 1. Book must not be archived.
     * 2. Book must belong to a favourite category.
     * 3. At least one copy must be AVAILABLE.
     * 4. Member must not have borrowed it before.
     */

    @EntityGraph(attributePaths = {"category"})
    @Query("""
            select b
            from Book b
            where b.archived = false

              and b.category.id in :categoryIds

              and exists (
                    select cp.id
                    from BookCopy cp
                    where cp.book.id = b.id
                      and cp.status = :copyStatus
              )

              and not exists (
                    select l.id
                    from Loan l
                    where l.user.id = :userId
                      and l.copy.book.id = b.id
              )

            order by b.id desc
            """)
    List<Book> findPersonalizedRecommendations(

            @Param("userId")
            Long userId,

            @Param("categoryIds")
            List<Long> categoryIds,

            @Param("copyStatus")
            CopyStatus copyStatus,

            Pageable pageable
    );

    // ==========================================
    // GENERAL BOOK RECOMMENDATIONS
    // ==========================================

    /*
     * Used for:
     *
     * 1. Newly registered members.
     * 2. Members without borrowing history.
     * 3. Filling remaining recommendation slots.
     *
     * Archived books, unavailable books and
     * previously borrowed books are excluded.
     */

    @EntityGraph(attributePaths = {"category"})
    @Query("""
            select b
            from Book b
            where b.archived = false

              and exists (
                    select cp.id
                    from BookCopy cp
                    where cp.book.id = b.id
                      and cp.status = :copyStatus
              )

              and not exists (
                    select l.id
                    from Loan l
                    where l.user.id = :userId
                      and l.copy.book.id = b.id
              )

            order by b.id desc
            """)
    List<Book> findGeneralRecommendations(

            @Param("userId")
            Long userId,

            @Param("copyStatus")
            CopyStatus copyStatus,

            Pageable pageable
    );
}