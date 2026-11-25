package com.smartlibrary.controller;

import com.smartlibrary.dto.SupportTicketDto.*;

import com.smartlibrary.entity.TicketStatus;

import com.smartlibrary.security.UserPrincipal;

import com.smartlibrary.service.SupportTicketService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import org.springframework.http.HttpStatus;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService ticketService;

    // ==========================================
    // MEMBER CREATE TICKET
    // ==========================================

    @PostMapping("/member/support/tickets")
    @PreAuthorize("hasRole('MEMBER')")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse createTicket(

            @AuthenticationPrincipal UserPrincipal me,

            @Valid @RequestBody CreateTicketRequest request

    ) {

        return ticketService.createTicket(
                me.getId(),
                request
        );
    }

    // ==========================================
    // MEMBER GET OWN TICKETS
    // ==========================================

    @GetMapping("/member/support/tickets")
    @PreAuthorize("hasRole('MEMBER')")
    public Page<TicketResponse> getMyTickets(

            @AuthenticationPrincipal UserPrincipal me,

            @RequestParam(required = false)
            TicketStatus status,

            @PageableDefault(size = 10)
            Pageable pageable

    ) {

        return ticketService.getMyTickets(
                me.getId(),
                status,
                pageable
        );
    }

    // ==========================================
    // MEMBER GET TICKET DETAILS
    // ==========================================

    @GetMapping("/member/support/tickets/{ticketId}")
    @PreAuthorize("hasRole('MEMBER')")
    public TicketResponse getMyTicket(

            @PathVariable Long ticketId,

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return ticketService.getMyTicket(
                ticketId,
                me.getId()
        );
    }

    // ==========================================
    // MEMBER GET CONVERSATION
    // ==========================================

    @GetMapping("/member/support/tickets/{ticketId}/replies")
    @PreAuthorize("hasRole('MEMBER')")
    public Page<ReplyResponse> getMemberReplies(

            @PathVariable Long ticketId,

            @AuthenticationPrincipal UserPrincipal me,

            @PageableDefault(size = 50)
            Pageable pageable

    ) {

        return ticketService.getReplies(
                ticketId,
                me.getId(),
                false,
                pageable
        );
    }

    // ==========================================
    // MEMBER SEND REPLY
    // ==========================================

    @PostMapping("/member/support/tickets/{ticketId}/replies")
    @PreAuthorize("hasRole('MEMBER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReplyResponse addMemberReply(

            @PathVariable Long ticketId,

            @AuthenticationPrincipal UserPrincipal me,

            @Valid @RequestBody ReplyRequest request

    ) {

        return ticketService.addMemberReply(
                ticketId,
                me.getId(),
                request
        );
    }

    // ==========================================
    // MEMBER CLOSE TICKET
    // ==========================================

    @PatchMapping("/member/support/tickets/{ticketId}/close")
    @PreAuthorize("hasRole('MEMBER')")
    public TicketResponse closeMyTicket(

            @PathVariable Long ticketId,

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return ticketService.closeMyTicket(
                ticketId,
                me.getId()
        );
    }

    // ==========================================
    // MEMBER REOPEN TICKET
    // ==========================================

    @PatchMapping("/member/support/tickets/{ticketId}/reopen")
    @PreAuthorize("hasRole('MEMBER')")
    public TicketResponse reopenMyTicket(

            @PathVariable Long ticketId,

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return ticketService.reopenMyTicket(
                ticketId,
                me.getId()
        );
    }

    // ==========================================
    // MEMBER SUMMARY
    // ==========================================

    @GetMapping("/member/support/summary")
    @PreAuthorize("hasRole('MEMBER')")
    public TicketSummaryResponse getMySummary(

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return ticketService.getMySummary(
                me.getId()
        );
    }

    // ==========================================
    // ADMIN GET ALL TICKETS
    // ==========================================

    @GetMapping("/admin/support/tickets")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<TicketResponse> getAllTickets(

            @AuthenticationPrincipal UserPrincipal me,

            @RequestParam(required = false)
            TicketStatus status,

            @PageableDefault(size = 20)
            Pageable pageable

    ) {

        return ticketService.getAllTickets(
                me.getId(),
                status,
                pageable
        );
    }

    // ==========================================
    // ADMIN TICKET DETAILS
    // ==========================================

    @GetMapping("/admin/support/tickets/{ticketId}")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketResponse getAdminTicket(

            @PathVariable Long ticketId,

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return ticketService.getAdminTicket(
                ticketId,
                me.getId()
        );
    }

    // ==========================================
    // ADMIN GET CONVERSATION
    // ==========================================

    @GetMapping("/admin/support/tickets/{ticketId}/replies")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<ReplyResponse> getAdminReplies(

            @PathVariable Long ticketId,

            @AuthenticationPrincipal UserPrincipal me,

            @PageableDefault(size = 50)
            Pageable pageable

    ) {

        return ticketService.getReplies(
                ticketId,
                me.getId(),
                true,
                pageable
        );
    }

    // ==========================================
    // ADMIN SEND REPLY
    // ==========================================

    @PostMapping("/admin/support/tickets/{ticketId}/replies")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReplyResponse addAdminReply(

            @PathVariable Long ticketId,

            @AuthenticationPrincipal UserPrincipal me,

            @Valid @RequestBody ReplyRequest request

    ) {

        return ticketService.addAdminReply(
                ticketId,
                me.getId(),
                request
        );
    }

    // ==========================================
    // ADMIN UPDATE STATUS
    // ==========================================

    @PatchMapping("/admin/support/tickets/{ticketId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketResponse updateStatus(

            @PathVariable Long ticketId,

            @AuthenticationPrincipal UserPrincipal me,

            @Valid @RequestBody UpdateStatusRequest request

    ) {

        return ticketService.updateStatus(
                ticketId,
                me.getId(),
                request
        );
    }

    // ==========================================
    // ADMIN UPDATE PRIORITY
    // ==========================================

    @PatchMapping("/admin/support/tickets/{ticketId}/priority")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketResponse updatePriority(

            @PathVariable Long ticketId,

            @AuthenticationPrincipal UserPrincipal me,

            @Valid @RequestBody UpdatePriorityRequest request

    ) {

        return ticketService.updatePriority(
                ticketId,
                me.getId(),
                request
        );
    }

    // ==========================================
    // ADMIN SUMMARY
    // ==========================================

    @GetMapping("/admin/support/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketSummaryResponse getAdminSummary(

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return ticketService.getAdminSummary(
                me.getId()
        );
    }
}