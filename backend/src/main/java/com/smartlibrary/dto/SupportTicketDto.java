package com.smartlibrary.dto;

import com.smartlibrary.entity.TicketCategory;
import com.smartlibrary.entity.TicketPriority;
import com.smartlibrary.entity.TicketStatus;

import jakarta.validation.constraints.*;

import java.time.Instant;

public final class SupportTicketDto {

    private SupportTicketDto() {
    }

    // ==========================================
    // CREATE TICKET
    // ==========================================

    public record CreateTicketRequest(

            @NotBlank
            @Size(min = 5, max = 150)
            String subject,

            @NotBlank
            @Size(min = 10, max = 3000)
            String description,

            @NotNull
            TicketCategory category,

            @NotNull
            TicketPriority priority

    ) {
    }

    // ==========================================
    // ADD REPLY
    // ==========================================

    public record ReplyRequest(

            @NotBlank
            @Size(max = 3000)
            String message

    ) {
    }

    // ==========================================
    // UPDATE STATUS
    // ==========================================

    public record UpdateStatusRequest(

            @NotNull
            TicketStatus status

    ) {
    }

    // ==========================================
    // UPDATE PRIORITY
    // ==========================================

    public record UpdatePriorityRequest(

            @NotNull
            TicketPriority priority

    ) {
    }

    // ==========================================
    // TICKET RESPONSE
    // ==========================================

    public record TicketResponse(

            Long id,

            String ticketNumber,

            Long userId,

            String memberName,

            String subject,

            String description,

            TicketCategory category,

            TicketPriority priority,

            TicketStatus status,

            Long assignedAdminId,

            Instant createdAt,

            Instant updatedAt,

            Instant resolvedAt,

            Instant closedAt

    ) {
    }

    // ==========================================
    // REPLY RESPONSE
    // ==========================================

    public record ReplyResponse(

            Long id,

            Long ticketId,

            Long senderId,

            String senderName,

            String senderRole,

            String message,

            Instant createdAt

    ) {
    }

    // ==========================================
    // TICKET SUMMARY
    // ==========================================

    public record TicketSummaryResponse(

            long totalTickets,

            long openTickets,

            long inProgressTickets,

            long resolvedTickets,

            long closedTickets

    ) {
    }
}