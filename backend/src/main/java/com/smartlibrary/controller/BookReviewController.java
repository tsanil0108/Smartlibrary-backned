package com.smartlibrary.controller;

import com.smartlibrary.dto.BookReviewDto.*;

import com.smartlibrary.entity.ReviewStatus;

import com.smartlibrary.security.UserPrincipal;

import com.smartlibrary.service.BookReviewService;

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
public class BookReviewController {

    private final BookReviewService reviewService;

    // ==========================================
    // PUBLIC BOOK REVIEWS
    // ==========================================

    @GetMapping("/books/{bookId}/reviews")
    public Page<ReviewResponse> getBookReviews(

            @PathVariable Long bookId,

            @PageableDefault(size = 10)
            Pageable pageable

    ) {

        return reviewService.getBookReviews(

                bookId,

                pageable
        );
    }

    // ==========================================
    // PUBLIC RATING SUMMARY
    // ==========================================

    @GetMapping("/books/{bookId}/ratings")
    public RatingSummaryResponse getRatingSummary(

            @PathVariable Long bookId

    ) {

        return reviewService.getRatingSummary(bookId);
    }

    // ==========================================
    // MEMBER CREATE REVIEW
    // ==========================================

    @PostMapping("/member/reviews/{bookId}")
    @PreAuthorize("hasRole('MEMBER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse createReview(

            @PathVariable Long bookId,

            @AuthenticationPrincipal UserPrincipal me,

            @Valid @RequestBody ReviewRequest request

    ) {

        return reviewService.createReview(

                me.getId(),

                bookId,

                request
        );
    }

    // ==========================================
    // MEMBER UPDATE REVIEW
    // ==========================================

    @PutMapping("/member/reviews/{reviewId}")
    @PreAuthorize("hasRole('MEMBER')")
    public ReviewResponse updateReview(

            @PathVariable Long reviewId,

            @AuthenticationPrincipal UserPrincipal me,

            @Valid @RequestBody ReviewRequest request

    ) {

        return reviewService.updateReview(

                reviewId,

                me.getId(),

                request
        );
    }

    // ==========================================
    // MEMBER DELETE REVIEW
    // ==========================================

    @DeleteMapping("/member/reviews/{reviewId}")
    @PreAuthorize("hasRole('MEMBER')")
    public MessageResponse deleteReview(

            @PathVariable Long reviewId,

            @AuthenticationPrincipal UserPrincipal me

    ) {

        reviewService.deleteReview(

                reviewId,

                me.getId()
        );

        return new MessageResponse(

                "Review deleted successfully"
        );
    }

    // ==========================================
    // MEMBER GET OWN REVIEWS
    // ==========================================

    @GetMapping("/member/reviews")
    @PreAuthorize("hasRole('MEMBER')")
    public Page<ReviewResponse> getMyReviews(

            @AuthenticationPrincipal UserPrincipal me,

            @PageableDefault(size = 10)
            Pageable pageable

    ) {

        return reviewService.getMyReviews(

                me.getId(),

                pageable
        );
    }

    // ==========================================
    // CHECK REVIEW ELIGIBILITY
    // ==========================================

    @GetMapping("/member/reviews/eligibility/{bookId}")
    @PreAuthorize("hasRole('MEMBER')")
    public ReviewEligibilityResponse checkEligibility(

            @PathVariable Long bookId,

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return reviewService.checkEligibility(

                me.getId(),

                bookId
        );
    }

    // ==========================================
    // ADMIN GET ALL REVIEWS
    // ==========================================

    @GetMapping("/admin/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<ReviewResponse> getAllReviews(

            @RequestParam(required = false)
            ReviewStatus status,

            @PageableDefault(size = 20)
            Pageable pageable

    ) {

        return reviewService.getAllReviews(

                status,

                pageable
        );
    }

    // ==========================================
    // ADMIN MODERATE REVIEW
    // ==========================================

    @PatchMapping("/admin/reviews/{reviewId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ReviewResponse moderateReview(

            @PathVariable Long reviewId,

            @AuthenticationPrincipal UserPrincipal me,

            @Valid @RequestBody ModerationRequest request

    ) {

        return reviewService.moderateReview(

                reviewId,

                me.getId(),

                request
        );
    }

    // ==========================================
    // ADMIN DELETE REVIEW
    // ==========================================

    @DeleteMapping("/admin/reviews/{reviewId}")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageResponse adminDeleteReview(

            @PathVariable Long reviewId,

            @AuthenticationPrincipal UserPrincipal me

    ) {

        reviewService.adminDeleteReview(

                reviewId,

                me.getId()
        );

        return new MessageResponse(

                "Review deleted successfully"
        );
    }
}