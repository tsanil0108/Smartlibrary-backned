package com.smartlibrary.event;

import java.time.LocalDate;

public record LoanNotificationEvent(

        String email,

        String memberName,

        String bookTitle,

        String subject,

        String message,

        LocalDate eventDate,

        LocalDate dueDate,

        String status

) {
}