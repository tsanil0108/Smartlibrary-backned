package com.smartlibrary.event;

import java.time.LocalDateTime;

public record ReservationReadyEvent(

        Long reservationId,
        Long userId,
        String email,
        String memberName,
        String bookTitle,
        LocalDateTime expiresAt

) {
}