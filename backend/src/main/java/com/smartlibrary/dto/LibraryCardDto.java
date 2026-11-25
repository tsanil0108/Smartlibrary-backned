package com.smartlibrary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class LibraryCardDto {

    private LibraryCardDto() {
    }

    // Member digital card response

    public record CardResponse(

            Long id,

            Long userId,

            String cardNumber,

            String memberName,

            String email,

            String membershipPlan,

            LocalDate membershipStartDate,

            LocalDate membershipEndDate,

            String status,

            boolean valid,

            String qrCode,

            LocalDateTime createdAt

    ) {
    }

    // Admin QR verification request

    public record VerifyCardRequest(

            @NotBlank(
                    message = "QR code is required"
            )
            @Size(max = 150)
            String qrCode

    ) {
    }

    // Admin verification response

    public record VerificationResponse(

            boolean valid,

            String message,

            String cardNumber,

            Long userId,

            String memberName,

            String membershipPlan,

            LocalDate membershipEndDate,

            String status

    ) {
    }

    // Card enable or disable request

    public record UpdateCardStatusRequest(

            boolean active

    ) {
    }
}