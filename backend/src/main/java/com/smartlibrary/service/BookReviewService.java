package com.smartlibrary.service;

import com.smartlibrary.dto.BookReviewDto.*;

import com.smartlibrary.entity.*;

import com.smartlibrary.repository.*;

import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.NotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.security.access.AccessDeniedException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookReviewService {

    private final BookReviewRepository reviewRepository;

    private final BookRepository bookRepository;

    private final UserRepository userRepository;

    private final LoanRepository loanRepository;

    private final AuditService auditService;

    // ==========================================
    // CREATE BOOK REVIEW
    // ==========================================

    @Transactional
    public ReviewResponse createReview(

            Long userId,

            Long bookId,

            ReviewRequest request

    ) {

        User user = lockActiveMember(userId);

        Book book = getAvailableBook(bookId);

        validateReview(request);

        // Only verified borrowers can review

        if (!hasBorrowedBook(userId, bookId)) {

            throw new BusinessRuleException(

                    "BOOK_NOT_BORROWED",

                    "You must borrow this book before "
                            + "submitting a review"
            );
        }

        // One review per member per book

        if (reviewRepository.existsByUser_IdAndBook_Id(

                userId,

                bookId

        )) {

            throw new BusinessRuleException(

                    "REVIEW_ALREADY_EXISTS",

                    "You have already reviewed this book. "
                            + "Please edit your existing review."
            );
        }

        BookReview review = new BookReview();

        review.setUser(user);

        review.setBook(book);

        review.setRating(request.rating());

        review.setComment(
                normalizeComment(request.comment())
        );

        review.setStatus(ReviewStatus.PUBLISHED);

        review = reviewRepository.saveAndFlush(review);

        return toResponse(review);
    }

    // ==========================================
    // UPDATE OWN REVIEW
    // ==========================================

    @Transactional
    public ReviewResponse updateReview(

            Long reviewId,

            Long userId,

            ReviewRequest request

    ) {

        lockActiveMember(userId);

        BookReview review = lockReview(reviewId);

        assertOwner(review, userId);

        getAvailableBook(review.getBook().getId());

        validateReview(request);

        review.setRating(request.rating());

        review.setComment(
                normalizeComment(request.comment())
        );

        // Preserve HIDDEN status after member edits.
        // Members cannot bypass admin moderation.

        review = reviewRepository.saveAndFlush(review);

        return toResponse(review);
    }

    // ==========================================
    // DELETE OWN REVIEW
    // ==========================================

    @Transactional
    public void deleteReview(

            Long reviewId,

            Long userId

    ) {

        lockActiveMember(userId);

        BookReview review = lockReview(reviewId);

        assertOwner(review, userId);

        reviewRepository.delete(review);
    }

    // ==========================================
    // GET PUBLIC BOOK REVIEWS
    // ==========================================

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getBookReviews(

            Long bookId,

            Pageable pageable

    ) {

        getAvailableBook(bookId);

        return reviewRepository

                .findByBook_IdAndStatusOrderByCreatedAtDescIdDesc(

                        bookId,

                        ReviewStatus.PUBLISHED,

                        pageable
                )

                .map(this::toResponse);
    }

    // ==========================================
    // GET MEMBER'S OWN REVIEWS
    // ==========================================

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getMyReviews(

            Long userId,

            Pageable pageable

    ) {

        return reviewRepository

                .findByUser_IdOrderByCreatedAtDescIdDesc(

                        userId,

                        pageable
                )

                .map(this::toResponse);
    }

    // ==========================================
    // CHECK REVIEW ELIGIBILITY
    // ==========================================

    @Transactional(readOnly = true)
    public ReviewEligibilityResponse checkEligibility(

            Long userId,

            Long bookId

    ) {

        getAvailableBook(bookId);

        User user = userRepository.findById(userId)

                .orElseThrow(

                        () -> new NotFoundException(
                                "User",
                                userId
                        )
                );

        var existingReview =
                reviewRepository.findByUser_IdAndBook_Id(

                        userId,

                        bookId
                );

        if (user.getRole() != Role.MEMBER

                || user.getStatus() != UserStatus.ACTIVE) {

            return new ReviewEligibilityResponse(

                    bookId,

                    false,

                    existingReview.isPresent(),

                    existingReview
                            .map(BookReview::getId)
                            .orElse(null),

                    "Active member required"
            );
        }

        if (existingReview.isPresent()) {

            return new ReviewEligibilityResponse(

                    bookId,

                    false,

                    true,

                    existingReview.get().getId(),

                    "You have already reviewed this book"
            );
        }

        boolean borrowed =
                hasBorrowedBook(userId, bookId);

        return new ReviewEligibilityResponse(

                bookId,

                borrowed,

                false,

                null,

                borrowed

                        ? "You can review this book"

                        : "Borrow this book before reviewing"
        );
    }

    // ==========================================
    // BOOK RATING SUMMARY
    // ==========================================

    @Transactional(readOnly = true)
    public RatingSummaryResponse getRatingSummary(

            Long bookId

    ) {

        getAvailableBook(bookId);

        List<Object[]> results =
                reviewRepository.ratingDistribution(

                        bookId,

                        ReviewStatus.PUBLISHED
                );

        Map<Integer, Long> ratingMap =
                new HashMap<>();

        long totalReviews = 0;

        long weightedTotal = 0;

        for (Object[] row : results) {

            int rating =
                    ((Number) row[0]).intValue();

            long count =
                    ((Number) row[1]).longValue();

            ratingMap.put(rating, count);

            totalReviews += count;

            weightedTotal += rating * count;
        }

        double averageRating =
                totalReviews == 0

                        ? 0.0

                        : roundOneDecimal(

                        (double) weightedTotal
                                / totalReviews
                );

        List<RatingCount> distribution =
                new ArrayList<>();

        for (int rating = 5; rating >= 1; rating--) {

            long count =
                    ratingMap.getOrDefault(
                            rating,
                            0L
                    );

            double percentage =
                    totalReviews == 0

                            ? 0.0

                            : roundOneDecimal(

                            count * 100.0
                                    / totalReviews
                    );

            distribution.add(

                    new RatingCount(

                            rating,

                            count,

                            percentage
                    )
            );
        }

        return new RatingSummaryResponse(

                bookId,

                averageRating,

                totalReviews,

                distribution
        );
    }

    // ==========================================
    // ADMIN GET ALL REVIEWS
    // ==========================================

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getAllReviews(

            ReviewStatus status,

            Pageable pageable

    ) {

        Page<BookReview> reviews;

        if (status == null) {

            reviews = reviewRepository

                    .findAllByOrderByCreatedAtDescIdDesc(
                            pageable
                    );

        } else {

            reviews = reviewRepository

                    .findByStatusOrderByCreatedAtDescIdDesc(

                            status,

                            pageable
                    );
        }

        return reviews.map(this::toResponse);
    }

    // ==========================================
    // ADMIN HIDE OR PUBLISH REVIEW
    // ==========================================

    @Transactional
    public ReviewResponse moderateReview(

            Long reviewId,

            Long adminId,

            ModerationRequest request

    ) {

        BookReview review = lockReview(reviewId);

        if (request == null || request.status() == null) {

            throw new BusinessRuleException(

                    "INVALID_REVIEW_STATUS",

                    "Review status is required"
            );
        }

        String reason = request.reason();

        if (reason != null && reason.length() > 500) {

            throw new BusinessRuleException(

                    "INVALID_MODERATION_REASON",

                    "Moderation reason cannot exceed "
                            + "500 characters"
            );
        }

        if (request.status() == ReviewStatus.HIDDEN

                && (reason == null || reason.isBlank())) {

            throw new BusinessRuleException(

                    "MODERATION_REASON_REQUIRED",

                    "Please provide a reason for hiding "
                            + "this review"
            );
        }

        review.setStatus(request.status());

        review.setModerationReason(

                request.status() == ReviewStatus.HIDDEN

                        ? reason.trim()

                        : null
        );

        review = reviewRepository.saveAndFlush(review);

        auditService.log(

                adminId,

                "REVIEW_MODERATED",

                "BOOK_REVIEW",

                review.getId(),

                "status=" + request.status()

                        + " reason="

                        + review.getModerationReason()
        );

        return toResponse(review);
    }

    // ==========================================
    // ADMIN DELETE REVIEW
    // ==========================================

    @Transactional
    public void adminDeleteReview(

            Long reviewId,

            Long adminId

    ) {

        BookReview review = lockReview(reviewId);

        auditService.log(

                adminId,

                "REVIEW_DELETED",

                "BOOK_REVIEW",

                reviewId,

                "Review deleted by admin"
        );

        reviewRepository.delete(review);
    }

    // ==========================================
    // VERIFIED BORROWER CHECK
    // ==========================================

    private boolean hasBorrowedBook(

            Long userId,

            Long bookId

    ) {

        return loanRepository

                .existsByUser_IdAndCopy_Book_Id(

                        userId,

                        bookId
                );
    }

    // ==========================================
    // VALIDATE MEMBER
    // ==========================================

    private User lockActiveMember(Long userId) {

        User user = userRepository

                .findByIdForUpdate(userId)

                .orElseThrow(

                        () -> new NotFoundException(
                                "User",
                                userId
                        )
                );

        if (user.getRole() != Role.MEMBER

                || user.getStatus() != UserStatus.ACTIVE) {

            throw new BusinessRuleException(

                    "INVALID_MEMBER",

                    "Active member required"
            );
        }

        return user;
    }

    // ==========================================
    // VALIDATE BOOK
    // ==========================================

    private Book getAvailableBook(Long bookId) {

        return bookRepository.findById(bookId)

                .filter(book -> !book.isArchived())

                .orElseThrow(

                        () -> new NotFoundException(

                                "Book",

                                bookId
                        )
                );
    }

    // ==========================================
    // LOCK REVIEW
    // ==========================================

    private BookReview lockReview(Long reviewId) {

        return reviewRepository

                .findByIdForUpdate(reviewId)

                .orElseThrow(

                        () -> new NotFoundException(

                                "Review",

                                reviewId
                        )
                );
    }

    // ==========================================
    // VERIFY REVIEW OWNERSHIP
    // ==========================================

    private void assertOwner(

            BookReview review,

            Long userId

    ) {

        if (!Objects.equals(

                review.getUser().getId(),

                userId

        )) {

            throw new AccessDeniedException(

                    "You can only modify your own review"
            );
        }
    }

    // ==========================================
    // VALIDATE RATING AND COMMENT
    // ==========================================

    private void validateReview(ReviewRequest request) {

        if (request == null

                || request.rating() == null

                || request.rating() < 1

                || request.rating() > 5) {

            throw new BusinessRuleException(

                    "INVALID_RATING",

                    "Rating must be between 1 and 5"
            );
        }

        if (request.comment() != null

                && request.comment().length() > 1000) {

            throw new BusinessRuleException(

                    "REVIEW_TOO_LONG",

                    "Review cannot exceed 1000 characters"
            );
        }
    }

    // ==========================================
    // NORMALIZE COMMENT
    // ==========================================

    private String normalizeComment(String comment) {

        if (comment == null || comment.isBlank()) {

            return null;
        }

        return comment.trim();
    }

    // ==========================================
    // ROUND RATING
    // ==========================================

    private double roundOneDecimal(double value) {

        return Math.round(value * 10.0) / 10.0;
    }

    // ==========================================
    // MAP ENTITY TO RESPONSE
    // ==========================================

    private ReviewResponse toResponse(BookReview review) {

        return new ReviewResponse(

                review.getId(),

                review.getBook().getId(),

                review.getBook().getTitle(),

                review.getUser().getId(),

                review.getUser().getName(),

                review.getRating(),

                review.getComment(),

                true,

                review.getStatus(),

                review.getCreatedAt(),

                review.getUpdatedAt()
        );
    }
}