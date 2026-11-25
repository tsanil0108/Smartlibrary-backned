package com.smartlibrary.dto;

import com.smartlibrary.entity.FineType;
import com.smartlibrary.entity.FineStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class FineDto {

    private FineDto() {
    }

    // Individual fine response

    public record FineResponse(

            Long id,

            Long userId,

            String memberName,

            Long loanId,

            Long bookId,

            String bookTitle,

            FineType type,

            FineStatus status,

            BigDecimal amount,

            long overdueDays,

            String description,

            String paymentMethod,

            String waiverReason,

            LocalDateTime createdAt,

            LocalDateTime paidAt,

            LocalDateTime waivedAt

    ) {
    }

    // Member fine dashboard

    public record FineSummaryResponse(

            long totalFines,

            long pendingFines,

            long paidFines,

            long waivedFines,

            BigDecimal totalAmount,

            BigDecimal pendingAmount,

            BigDecimal paidAmount,

            BigDecimal waivedAmount,

            List<FineResponse> fines

    ) {
    }

    // Admin fine waiver

    public record WaiveFineRequest(

            @NotBlank(
                    message = "Waiver reason is required"
            )
            @Size(max = 500)
            String reason

    ) {
    }

    // Current overdue estimate

    public record FineEstimateResponse(

            Long loanId,

            long overdueDays,

            BigDecimal overdueFine,

            BigDecimal damageCharge,

            BigDecimal lostCharge,

            BigDecimal totalFine

    ) {
    }
}