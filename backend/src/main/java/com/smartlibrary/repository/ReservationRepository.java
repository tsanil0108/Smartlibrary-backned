package com.smartlibrary.repository;

import com.smartlibrary.entity.Reservation;
import com.smartlibrary.entity.ReservationStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUser_IdOrderByCreatedAtDesc(
            Long userId
    );

    List<Reservation> findByBook_IdAndStatusOrderByCreatedAtAscIdAsc(
            Long bookId,
            ReservationStatus status
    );

    Optional<Reservation> findFirstByUser_IdAndBook_IdAndStatusIn(
            Long userId,
            Long bookId,
            Collection<ReservationStatus> statuses
    );

    long countByBook_IdAndStatus(
            Long bookId,
            ReservationStatus status
    );

    long countByUser_IdAndStatusIn(
            Long userId,
            Collection<ReservationStatus> statuses
    );

    @Query("""
        SELECT DISTINCT r.book.id
        FROM Reservation r
        WHERE r.status = :status
        AND r.expiresAt <= :now
        """)
    List<Long> findExpiredBookIds(
            ReservationStatus status,
            LocalDateTime now
    );

    List<Reservation> findAllByOrderByCreatedAtDesc();
}