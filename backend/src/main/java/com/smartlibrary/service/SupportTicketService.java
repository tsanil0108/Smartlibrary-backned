package com.smartlibrary.service;

import com.smartlibrary.dto.SupportTicketDto.*;

import com.smartlibrary.entity.*;

import com.smartlibrary.event.TicketReplyEvent;

import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.NotFoundException;

import com.smartlibrary.repository.*;

import java.time.Clock;
import java.time.Instant;

import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.security.access.AccessDeniedException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final SupportTicketRepository ticketRepository;

    private final TicketReplyRepository replyRepository;

    private final UserRepository userRepository;

    private final AuditService auditService;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    private static final int MAX_ACTIVE_TICKETS = 5;

    private static final List<TicketStatus> ACTIVE_STATUSES =
            List.of(
                    TicketStatus.OPEN,
                    TicketStatus.IN_PROGRESS
            );

    // ==========================================
    // MEMBER CREATE TICKET
    // ==========================================

    @Transactional
    public TicketResponse createTicket(

            Long userId,

            CreateTicketRequest request

    ) {

        User user = userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "User",
                                userId
                        )
                );

        validateUser(user, Role.MEMBER);

        long activeTickets =
                ticketRepository.countByUser_IdAndStatusIn(
                        userId,
                        ACTIVE_STATUSES
                );

        if (activeTickets >= MAX_ACTIVE_TICKETS) {

            throw new BusinessRuleException(

                    "TICKET_LIMIT_REACHED",

                    "You can have a maximum of 5 active tickets"
            );
        }

        String subject = request.subject().trim();

        String description = request.description().trim();

        if (subject.length() < 5
                || description.length() < 10) {

            throw new BusinessRuleException(

                    "INVALID_TICKET_CONTENT",

                    "Please provide a valid subject and description"
            );
        }

        SupportTicket ticket = new SupportTicket();

        ticket.setUser(user);

        ticket.setSubject(subject);

        ticket.setDescription(description);

        ticket.setCategory(request.category());

        ticket.setPriority(request.priority());

        ticket.setStatus(TicketStatus.OPEN);

        ticket.setCreatedAt(Instant.now(clock));

        ticket.setUpdatedAt(Instant.now(clock));

        ticket = ticketRepository.saveAndFlush(ticket);

        return toTicketResponse(ticket);
    }

    // ==========================================
    // MEMBER GET OWN TICKETS
    // ==========================================

    @Transactional(readOnly = true)
    public Page<TicketResponse> getMyTickets(

            Long userId,

            TicketStatus status,

            Pageable pageable

    ) {

        getActiveUser(userId, Role.MEMBER);

        Page<SupportTicket> tickets;

        if (status == null) {

            tickets = ticketRepository
                    .findByUser_IdOrderByUpdatedAtDescIdDesc(
                            userId,
                            pageable
                    );

        } else {

            tickets = ticketRepository
                    .findByUser_IdAndStatusOrderByUpdatedAtDescIdDesc(
                            userId,
                            status,
                            pageable
                    );
        }

        return tickets.map(this::toTicketResponse);
    }

    // ==========================================
    // MEMBER GET TICKET DETAILS
    // ==========================================

    @Transactional(readOnly = true)
    public TicketResponse getMyTicket(

            Long ticketId,

            Long userId

    ) {

        getActiveUser(userId, Role.MEMBER);

        SupportTicket ticket =
                ticketRepository.findByIdAndUser_Id(
                                ticketId,
                                userId
                        )
                        .orElseThrow(
                                () -> new NotFoundException(
                                        "Ticket",
                                        ticketId
                                )
                        );

        return toTicketResponse(ticket);
    }

    // ==========================================
    // GET TICKET CONVERSATION
    // ==========================================

    @Transactional(readOnly = true)
    public Page<ReplyResponse> getReplies(

            Long ticketId,

            Long actorId,

            boolean admin,

            Pageable pageable

    ) {

        getActiveUser(
                actorId,
                admin ? Role.ADMIN : Role.MEMBER
        );

        SupportTicket ticket =
                ticketRepository.findById(ticketId)
                        .orElseThrow(
                                () -> new NotFoundException(
                                        "Ticket",
                                        ticketId
                                )
                        );

        if (!admin) {
            assertOwner(ticket, actorId);
        }

        return replyRepository
                .findByTicket_IdOrderByCreatedAtAscIdAsc(
                        ticketId,
                        pageable
                )
                .map(this::toReplyResponse);
    }

    // ==========================================
    // MEMBER REPLY
    // ==========================================

    @Transactional
    public ReplyResponse addMemberReply(

            Long ticketId,

            Long userId,

            ReplyRequest request

    ) {

        User user =
                getActiveUser(userId, Role.MEMBER);

        SupportTicket ticket = lockTicket(ticketId);

        assertOwner(ticket, userId);

        requireReplyAllowed(ticket);

        TicketReply reply = createReply(
                ticket,
                user,
                request.message()
        );

        ticket.setUpdatedAt(Instant.now(clock));

        ticketRepository.save(ticket);

        return toReplyResponse(reply);
    }

    // ==========================================
    // ADMIN REPLY
    // ==========================================

    @Transactional
    public ReplyResponse addAdminReply(

            Long ticketId,

            Long adminId,

            ReplyRequest request

    ) {

        User admin =
                getActiveUser(adminId, Role.ADMIN);

        SupportTicket ticket = lockTicket(ticketId);

        requireReplyAllowed(ticket);

        TicketReply reply = createReply(
                ticket,
                admin,
                request.message()
        );

        if (ticket.getStatus() == TicketStatus.OPEN) {

            ticket.setStatus(
                    TicketStatus.IN_PROGRESS
            );
        }

        ticket.setAssignedAdminId(adminId);

        ticket.setUpdatedAt(Instant.now(clock));

        ticketRepository.save(ticket);

        auditService.log(

                adminId,

                "TICKET_REPLIED",

                "SUPPORT_TICKET",

                ticketId,

                "Admin replied to support ticket"
        );

        User member = ticket.getUser();

        eventPublisher.publishEvent(

                new TicketReplyEvent(

                        ticket.getId(),

                        ticket.getTicketNumber(),

                        member.getEmail(),

                        member.getName(),

                        ticket.getSubject(),

                        reply.getMessage()
                )
        );

        return toReplyResponse(reply);
    }

    // ==========================================
    // MEMBER CLOSE TICKET
    // ==========================================

    @Transactional
    public TicketResponse closeMyTicket(

            Long ticketId,

            Long userId

    ) {

        getActiveUser(userId, Role.MEMBER);

        SupportTicket ticket = lockTicket(ticketId);

        assertOwner(ticket, userId);

        if (ticket.getStatus() == TicketStatus.CLOSED) {

            return toTicketResponse(ticket);
        }

        ticket.setStatus(TicketStatus.CLOSED);

        ticket.setClosedAt(Instant.now(clock));

        ticket.setUpdatedAt(Instant.now(clock));

        return toTicketResponse(
                ticketRepository.saveAndFlush(ticket)
        );
    }

    // ==========================================
    // MEMBER REOPEN RESOLVED TICKET
    // ==========================================

    @Transactional
    public TicketResponse reopenMyTicket(

            Long ticketId,

            Long userId

    ) {

        User user = userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "User",
                                userId
                        )
                );

        validateUser(user, Role.MEMBER);

        SupportTicket ticket = lockTicket(ticketId);

        assertOwner(ticket, userId);

        if (ticket.getStatus() != TicketStatus.RESOLVED) {

            throw new BusinessRuleException(

                    "TICKET_CANNOT_REOPEN",

                    "Only resolved tickets can be reopened"
            );
        }

        long activeTickets =
                ticketRepository.countByUser_IdAndStatusIn(
                        userId,
                        ACTIVE_STATUSES
                );

        if (activeTickets >= MAX_ACTIVE_TICKETS) {

            throw new BusinessRuleException(

                    "TICKET_LIMIT_REACHED",

                    "Maximum active ticket limit reached"
            );
        }

        ticket.setStatus(TicketStatus.OPEN);

        ticket.setResolvedAt(null);

        ticket.setClosedAt(null);

        ticket.setAssignedAdminId(null);

        ticket.setUpdatedAt(Instant.now(clock));

        return toTicketResponse(
                ticketRepository.saveAndFlush(ticket)
        );
    }

    // ==========================================
    // MEMBER TICKET SUMMARY
    // ==========================================

    @Transactional(readOnly = true)
    public TicketSummaryResponse getMySummary(
            Long userId
    ) {

        getActiveUser(userId, Role.MEMBER);

        return new TicketSummaryResponse(

                ticketRepository.countByUser_Id(userId),

                ticketRepository.countByUser_IdAndStatus(
                        userId,
                        TicketStatus.OPEN
                ),

                ticketRepository.countByUser_IdAndStatus(
                        userId,
                        TicketStatus.IN_PROGRESS
                ),

                ticketRepository.countByUser_IdAndStatus(
                        userId,
                        TicketStatus.RESOLVED
                ),

                ticketRepository.countByUser_IdAndStatus(
                        userId,
                        TicketStatus.CLOSED
                )
        );
    }

    // ==========================================
    // ADMIN GET ALL TICKETS
    // ==========================================

    @Transactional(readOnly = true)
    public Page<TicketResponse> getAllTickets(

            Long adminId,

            TicketStatus status,

            Pageable pageable

    ) {

        getActiveUser(adminId, Role.ADMIN);

        Page<SupportTicket> tickets;

        if (status == null) {

            tickets = ticketRepository
                    .findAllByOrderByUpdatedAtDescIdDesc(
                            pageable
                    );

        } else {

            tickets = ticketRepository
                    .findByStatusOrderByUpdatedAtDescIdDesc(
                            status,
                            pageable
                    );
        }

        return tickets.map(this::toTicketResponse);
    }

    // ==========================================
    // ADMIN GET TICKET DETAILS
    // ==========================================

    @Transactional(readOnly = true)
    public TicketResponse getAdminTicket(

            Long ticketId,

            Long adminId

    ) {

        getActiveUser(adminId, Role.ADMIN);

        SupportTicket ticket =
                ticketRepository.findById(ticketId)
                        .orElseThrow(
                                () -> new NotFoundException(
                                        "Ticket",
                                        ticketId
                                )
                        );

        return toTicketResponse(ticket);
    }

    // ==========================================
    // ADMIN UPDATE TICKET STATUS
    // ==========================================

    @Transactional
    public TicketResponse updateStatus(

            Long ticketId,

            Long adminId,

            UpdateStatusRequest request

    ) {

        getActiveUser(adminId, Role.ADMIN);

        SupportTicket ticket = lockTicket(ticketId);

        TicketStatus oldStatus = ticket.getStatus();

        TicketStatus newStatus = request.status();

        if (oldStatus == newStatus) {

            return toTicketResponse(ticket);
        }

        validateStatusTransition(
                oldStatus,
                newStatus
        );

        ticket.setStatus(newStatus);

        ticket.setAssignedAdminId(adminId);

        ticket.setUpdatedAt(Instant.now(clock));

        if (newStatus == TicketStatus.RESOLVED) {

            ticket.setResolvedAt(Instant.now(clock));

            ticket.setClosedAt(null);

        } else if (newStatus == TicketStatus.CLOSED) {

            ticket.setClosedAt(Instant.now(clock));

        } else {

            ticket.setResolvedAt(null);

            ticket.setClosedAt(null);
        }

        ticket = ticketRepository.saveAndFlush(ticket);

        auditService.log(

                adminId,

                "TICKET_STATUS_UPDATED",

                "SUPPORT_TICKET",

                ticketId,

                "Old status=" + oldStatus
                        + ", New status=" + newStatus
        );

        return toTicketResponse(ticket);
    }

    // ==========================================
    // ADMIN UPDATE PRIORITY
    // ==========================================

    @Transactional
    public TicketResponse updatePriority(

            Long ticketId,

            Long adminId,

            UpdatePriorityRequest request

    ) {

        getActiveUser(adminId, Role.ADMIN);

        SupportTicket ticket = lockTicket(ticketId);

        if (ticket.getStatus() == TicketStatus.CLOSED) {

            throw new BusinessRuleException(

                    "TICKET_CLOSED",

                    "Closed ticket cannot be modified"
            );
        }

        ticket.setPriority(request.priority());

        ticket.setUpdatedAt(Instant.now(clock));

        ticket = ticketRepository.saveAndFlush(ticket);

        auditService.log(

                adminId,

                "TICKET_PRIORITY_UPDATED",

                "SUPPORT_TICKET",

                ticketId,

                "Priority=" + request.priority()
        );

        return toTicketResponse(ticket);
    }

    // ==========================================
    // ADMIN TICKET SUMMARY
    // ==========================================

    @Transactional(readOnly = true)
    public TicketSummaryResponse getAdminSummary(
            Long adminId
    ) {

        getActiveUser(adminId, Role.ADMIN);

        return new TicketSummaryResponse(

                ticketRepository.count(),

                ticketRepository.countByStatus(
                        TicketStatus.OPEN
                ),

                ticketRepository.countByStatus(
                        TicketStatus.IN_PROGRESS
                ),

                ticketRepository.countByStatus(
                        TicketStatus.RESOLVED
                ),

                ticketRepository.countByStatus(
                        TicketStatus.CLOSED
                )
        );
    }

    // ==========================================
    // CREATE REPLY ENTITY
    // ==========================================

    private TicketReply createReply(

            SupportTicket ticket,

            User sender,

            String message

    ) {

        if (message == null
                || message.isBlank()
                || message.length() > 3000) {

            throw new BusinessRuleException(

                    "INVALID_REPLY",

                    "Reply must contain 1 to 3000 characters"
            );
        }

        TicketReply reply = new TicketReply();

        reply.setTicket(ticket);

        reply.setSender(sender);

        reply.setSenderRole(sender.getRole());

        reply.setMessage(message.trim());

        reply.setCreatedAt(Instant.now(clock));

        return replyRepository.saveAndFlush(reply);
    }

    // ==========================================
    // VALIDATE STATUS TRANSITION
    // ==========================================

    private void validateStatusTransition(

            TicketStatus oldStatus,

            TicketStatus newStatus

    ) {

        boolean allowed = switch (oldStatus) {

            case OPEN ->

                    newStatus == TicketStatus.IN_PROGRESS

                            || newStatus == TicketStatus.RESOLVED

                            || newStatus == TicketStatus.CLOSED;

            case IN_PROGRESS ->

                    newStatus == TicketStatus.RESOLVED

                            || newStatus == TicketStatus.CLOSED;

            case RESOLVED ->

                    newStatus == TicketStatus.CLOSED;

            case CLOSED -> false;
        };

        if (!allowed) {

            throw new BusinessRuleException(

                    "INVALID_TICKET_TRANSITION",

                    "Cannot change ticket from "
                            + oldStatus
                            + " to "
                            + newStatus
            );
        }
    }

    // ==========================================
    // VALIDATE REPLY STATUS
    // ==========================================

    private void requireReplyAllowed(
            SupportTicket ticket
    ) {

        if (!ACTIVE_STATUSES.contains(
                ticket.getStatus()
        )) {

            throw new BusinessRuleException(

                    "TICKET_NOT_ACTIVE",

                    "Reopen the resolved ticket before replying. "
                            + "Closed tickets cannot receive replies."
            );
        }
    }

    // ==========================================
    // LOCK TICKET
    // ==========================================

    private SupportTicket lockTicket(
            Long ticketId
    ) {

        return ticketRepository
                .findByIdForUpdate(ticketId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "Ticket",
                                ticketId
                        )
                );
    }

    // ==========================================
    // VALIDATE OWNERSHIP
    // ==========================================

    private void assertOwner(

            SupportTicket ticket,

            Long userId

    ) {

        if (!Objects.equals(
                ticket.getUser().getId(),
                userId
        )) {

            throw new AccessDeniedException(

                    "You can only access your own tickets"
            );
        }
    }

    // ==========================================
    // VALIDATE USER
    // ==========================================

    private User getActiveUser(

            Long userId,

            Role role

    ) {

        User user = userRepository.findById(userId)

                .orElseThrow(

                        () -> new NotFoundException(
                                "User",
                                userId
                        )
                );

        validateUser(user, role);

        return user;
    }

    private void validateUser(

            User user,

            Role role

    ) {

        if (user.getRole() != role

                || user.getStatus() != UserStatus.ACTIVE) {

            throw new AccessDeniedException(

                    "Active " + role.name() + " account required"
            );
        }
    }

    // ==========================================
    // MAP TICKET RESPONSE
    // ==========================================

    private TicketResponse toTicketResponse(
            SupportTicket ticket
    ) {

        return new TicketResponse(

                ticket.getId(),

                ticket.getTicketNumber(),

                ticket.getUser().getId(),

                ticket.getUser().getName(),

                ticket.getSubject(),

                ticket.getDescription(),

                ticket.getCategory(),

                ticket.getPriority(),

                ticket.getStatus(),

                ticket.getAssignedAdminId(),

                ticket.getCreatedAt(),

                ticket.getUpdatedAt(),

                ticket.getResolvedAt(),

                ticket.getClosedAt()
        );
    }

    // ==========================================
    // MAP REPLY RESPONSE
    // ==========================================

    private ReplyResponse toReplyResponse(
            TicketReply reply
    ) {

        return new ReplyResponse(

                reply.getId(),

                reply.getTicket().getId(),

                reply.getSender().getId(),

                reply.getSender().getName(),

                reply.getSenderRole().name(),

                reply.getMessage(),

                reply.getCreatedAt()
        );
    }
}