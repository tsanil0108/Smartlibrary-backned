package com.smartlibrary.event;

import java.time.LocalDate;

public record BookBorrowedEvent(

        String email,

        String memberName,

        String bookTitle,

        LocalDate borrowDate,

        LocalDate dueDate

) {
}