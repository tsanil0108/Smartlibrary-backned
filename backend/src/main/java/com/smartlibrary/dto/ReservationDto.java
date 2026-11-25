package com.smartlibrary.dto;

import com.smartlibrary.entity.ReservationStatus;
import java.time.LocalDateTime;

public final class ReservationDto {

    private ReservationDto() {
    }

    public record ReservationResponse(

            Long id,
            Long bookId,
            String bookTitle,
            String author,
            Long userId,
            String memberName,
            ReservationStatus status,
            int queuePosition,
            LocalDateTime createdAt,
            LocalDateTime readyAt,
            LocalDateTime expiresAt

    ) {
    }
}