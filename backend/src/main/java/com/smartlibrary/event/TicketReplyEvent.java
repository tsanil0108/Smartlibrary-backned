package com.smartlibrary.event;

public record TicketReplyEvent(

        Long ticketId,

        String ticketNumber,

        String memberEmail,

        String memberName,

        String subject,

        String replyMessage

) {
}