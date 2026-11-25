package com.smartlibrary.repository;

import com.smartlibrary.dto.projection.CopyCount;
import com.smartlibrary.entity.BookCopy;
import com.smartlibrary.entity.CopyStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

    List<BookCopy> findByBook_IdOrderByIdAsc(Long bookId);

    long countByBook_Id(Long bookId);

    long countByStatus(CopyStatus status);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select c from BookCopy c
            where c.book.id = :bookId and c.status = com.smartlibrary.entity.CopyStatus.AVAILABLE
            order by c.id""")
    List<BookCopy> findAvailableForUpdate(@Param("bookId") Long bookId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookCopy c where c.id = :id")
    Optional<BookCopy> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select new com.smartlibrary.dto.projection.CopyCount(
                c.book.id, count(c),
                sum(case when c.status = com.smartlibrary.entity.CopyStatus.AVAILABLE then 1L else 0L end))
            from BookCopy c where c.book.id in :ids group by c.book.id""")
    List<CopyCount> countByBookIds(@Param("ids") Collection<Long> ids);

    @Query("select c.status, count(c) from BookCopy c group by c.status")
    List<Object[]> countGroupedByStatus();
}
