package com.smartlibrary.repository;

import com.smartlibrary.entity.Fine;
import com.smartlibrary.entity.FineType;
import com.smartlibrary.entity.FineStatus;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FineRepository
        extends JpaRepository<Fine, Long> {

    List<Fine> findByUser_IdOrderByCreatedAtDesc(
            Long userId
    );

    List<Fine> findAllByOrderByCreatedAtDesc();

    List<Fine> findByStatusOrderByCreatedAtDesc(
            FineStatus status
    );

    List<Fine> findByLoan_Id(Long loanId);

    boolean existsByLoan_IdAndType(
            Long loanId,
            FineType type
    );

    boolean existsByUser_IdAndStatus(
            Long userId,
            FineStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT f
        FROM Fine f
        WHERE f.id = :id
        """)
    Optional<Fine> findByIdForUpdate(
            @Param("id") Long id
    );
}