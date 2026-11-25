package com.smartlibrary.repository;

import com.smartlibrary.entity.TicketReply;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketReplyRepository
        extends JpaRepository<TicketReply, Long> {

    @EntityGraph(attributePaths = "sender")
    Page<TicketReply>
    findByTicket_IdOrderByCreatedAtAscIdAsc(

            Long ticketId,

            Pageable pageable
    );

    long countByTicket_Id(
            Long ticketId
    );
}