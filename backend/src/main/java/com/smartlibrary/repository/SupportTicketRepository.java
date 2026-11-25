package com.smartlibrary.repository;

import com.smartlibrary.entity.SupportTicket;
import com.smartlibrary.entity.TicketStatus;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SupportTicketRepository
        extends JpaRepository<SupportTicket, Long> {

    // ==========================================
    // MEMBER TICKETS
    // ==========================================

    @EntityGraph(attributePaths = "user")
    Page<SupportTicket>
    findByUser_IdOrderByUpdatedAtDescIdDesc(

            Long userId,

            Pageable pageable
    );

    @EntityGraph(attributePaths = "user")
    Page<SupportTicket>
    findByUser_IdAndStatusOrderByUpdatedAtDescIdDesc(

            Long userId,

            TicketStatus status,

            Pageable pageable
    );

    Optional<SupportTicket> findByIdAndUser_Id(

            Long id,

            Long userId
    );

    // ==========================================
    // ADMIN TICKETS
    // ==========================================

    @EntityGraph(attributePaths = "user")
    Page<SupportTicket>
    findAllByOrderByUpdatedAtDescIdDesc(
            Pageable pageable
    );

    @EntityGraph(attributePaths = "user")
    Page<SupportTicket>
    findByStatusOrderByUpdatedAtDescIdDesc(

            TicketStatus status,

            Pageable pageable
    );

    // ==========================================
    // LOCK TICKET
    // ==========================================

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from SupportTicket t
            where t.id = :id
            """)
    Optional<SupportTicket> findByIdForUpdate(
            @Param("id") Long id
    );

    // ==========================================
    // MEMBER TICKET COUNTS
    // ==========================================

    long countByUser_Id(Long userId);

    long countByUser_IdAndStatus(

            Long userId,

            TicketStatus status
    );

    long countByUser_IdAndStatusIn(

            Long userId,

            List<TicketStatus> statuses
    );

    // ==========================================
    // ADMIN TICKET COUNTS
    // ==========================================

    long countByStatus(
            TicketStatus status
    );
}