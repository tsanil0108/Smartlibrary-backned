package com.smartlibrary.dto;

import java.time.LocalDateTime;

public final class WishlistDto {

    private WishlistDto() {
    }

    // Wishlist book details

    public record WishlistResponse(

            Long id,

            Long bookId,

            String title,

            String author,

            String isbn,

            long availableCopies,

            boolean available,

            LocalDateTime addedAt

    ) {
    }

    // Check favourite status

    public record WishlistStatusResponse(

            Long bookId,

            boolean wishlisted

    ) {
    }

    // Dashboard wishlist count

    public record WishlistCountResponse(

            long total

    ) {
    }

    // Success message

    public record MessageResponse(

            String message

    ) {
    }
}