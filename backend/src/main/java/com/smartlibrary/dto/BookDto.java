package com.smartlibrary.dto;

import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.BookCopy;
import com.smartlibrary.entity.CopyStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class BookDto {
    private BookDto() {}

    public record CreateBookRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 180) String author,
            @Size(max = 20) String isbn,
            @NotNull Long categoryId,
            @Size(max = 2000) String description,
            @Size(max = 500) String coverUrl,
            @Min(0) @Max(100) Integer copies,          // null => 1
            @Size(max = 60) String shelfLocation) {}

    public record UpdateBookRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 180) String author,
            @Size(max = 20) String isbn,
            @NotNull Long categoryId,
            @Size(max = 2000) String description,
            @Size(max = 500) String coverUrl) {}

    public record BookResponse(Long id, String title, String author, String isbn, Long categoryId, String categoryName,
                               String description, String coverUrl, long totalCopies, long availableCopies,
                               boolean available, boolean archived) {
        public static BookResponse from(Book b, long total, long available) {
            return new BookResponse(b.getId(), b.getTitle(), b.getAuthor(), b.getIsbn(),
                    b.getCategory().getId(), b.getCategory().getName(), b.getDescription(), b.getCoverUrl(),
                    total, available, available > 0, b.isArchived());
        }
    }

    public record AddCopiesRequest(@Min(1) @Max(100) int count, @Size(max = 60) String shelfLocation) {}

    public record UpdateCopyStatusRequest(@NotNull CopyStatus status) {}

    public record CopyResponse(Long id, Long bookId, String accessionNo, CopyStatus status, String shelfLocation) {
        public static CopyResponse from(BookCopy c) {
            return new CopyResponse(c.getId(), c.getBook().getId(), c.getAccessionNo(), c.getStatus(), c.getShelfLocation());
        }
    }
}
