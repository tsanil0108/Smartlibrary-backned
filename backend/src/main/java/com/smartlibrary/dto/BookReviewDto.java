package com.smartlibrary.dto;

import com.smartlibrary.entity.ReviewStatus;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class BookReviewDto {

    private BookReviewDto() {
    }

    // Create and update review

    public record ReviewRequest(

            @NotNull
            @Min(1)
            @Max(5)
            Integer rating,

            @Size(max = 1000)
            String comment

    ) {
    }

    // Review response

    public record ReviewResponse(

            Long id,

            Long bookId,

            String bookTitle,

            Long userId,

            String memberName,

            int rating,

            String comment,

            boolean verifiedBorrower,

            ReviewStatus status,

            Instant createdAt,

            Instant updatedAt

    ) {
    }

    // Individual rating distribution

    public record RatingCount(

            int rating,

            long count,

            double percentage

    ) {
    }

    // Book rating summary

    public record RatingSummaryResponse(

            Long bookId,

            double averageRating,

            long totalReviews,

            List<RatingCount> distribution

    ) {
    }

    // Review eligibility

    public record ReviewEligibilityResponse(

            Long bookId,

            boolean canReview,

            boolean alreadyReviewed,

            Long reviewId,

            String message

    ) {
    }

    // Admin moderation

    public record ModerationRequest(

            @NotNull
            ReviewStatus status,

            @Size(max = 500)
            String reason

    ) {
    }

    public record MessageResponse(

            String message

    ) {
    }
}