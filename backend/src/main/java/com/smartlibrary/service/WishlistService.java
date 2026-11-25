package com.smartlibrary.service;

import com.smartlibrary.dto.WishlistDto.WishlistResponse;
import com.smartlibrary.dto.WishlistDto.WishlistStatusResponse;
import com.smartlibrary.dto.WishlistDto.WishlistCountResponse;
import com.smartlibrary.dto.WishlistDto.MessageResponse;

import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.User;
import com.smartlibrary.entity.Wishlist;
import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.UserStatus;

import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.NotFoundException;

import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.repository.WishlistRepository;

import jakarta.persistence.EntityManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;

    private final UserRepository userRepository;

    private final BookRepository bookRepository;

    private final EntityManager entityManager;

    private final Clock clock;

    // ==========================================
    // ADD BOOK TO WISHLIST
    // ==========================================

    @Transactional
    public WishlistResponse add(
            Long userId,
            Long bookId
    ) {

        User user = lockMember(userId);

        Book book = bookRepository.findById(bookId)
                .filter(b -> !b.isArchived())
                .orElseThrow(
                        () -> new NotFoundException(
                                "Book",
                                bookId
                        )
                );

        // Prevent duplicate wishlist entries

        Wishlist existing = wishlistRepository
                .findByUser_IdAndBook_Id(userId, bookId)
                .orElse(null);

        if (existing != null) {
            return toResponse(existing);
        }

        Wishlist wishlist = new Wishlist();

        wishlist.setUser(user);

        wishlist.setBook(book);

        wishlist.setCreatedAt(
                LocalDateTime.now(clock)
        );

        wishlistRepository.save(wishlist);

        return toResponse(wishlist);
    }

    // ==========================================
    // GET ALL WISHLIST BOOKS
    // ==========================================

    @Transactional(readOnly = true)
    public List<WishlistResponse> getMyWishlist(
            Long userId
    ) {

        return wishlistRepository
                .findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(item -> !item.getBook().isArchived())
                .map(this::toResponse)
                .toList();
    }

    // ==========================================
    // REMOVE BOOK FROM WISHLIST
    // ==========================================

    @Transactional
    public MessageResponse remove(
            Long userId,
            Long bookId
    ) {

        lockMember(userId);

        wishlistRepository.deleteByUser_IdAndBook_Id(
                userId,
                bookId
        );

        return new MessageResponse(
                "Book removed from wishlist"
        );
    }

    // ==========================================
    // CHECK WISHLIST STATUS
    // ==========================================

    @Transactional(readOnly = true)
    public WishlistStatusResponse check(
            Long userId,
            Long bookId
    ) {

        boolean exists = wishlistRepository
                .existsByUser_IdAndBook_Id(
                        userId,
                        bookId
                );

        return new WishlistStatusResponse(
                bookId,
                exists
        );
    }

    // ==========================================
    // WISHLIST COUNT FOR MEMBER DASHBOARD
    // ==========================================

    @Transactional(readOnly = true)
    public WishlistCountResponse count(Long userId) {

        long total = entityManager.createQuery(
                        """
                        SELECT COUNT(w)
                        FROM Wishlist w
                        WHERE w.user.id = :userId
                        AND w.book.archived = false
                        """,
                        Long.class
                )
                .setParameter("userId", userId)
                .getSingleResult();

        return new WishlistCountResponse(total);
    }

    // ==========================================
    // VALIDATE AND LOCK MEMBER
    // ==========================================

    private User lockMember(Long userId) {

        User user = userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(
                        () -> new NotFoundException(
                                "User",
                                userId
                        )
                );

        if (user.getRole() != Role.MEMBER) {

            throw new BusinessRuleException(
                    "NOT_A_MEMBER",
                    "Only members can manage wishlist"
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {

            throw new BusinessRuleException(
                    "ACCOUNT_INACTIVE",
                    "Your account is not active"
            );
        }

        return user;
    }

    // ==========================================
    // GET AVAILABLE BOOK COPIES
    // ==========================================

    private long availableCopies(Long bookId) {

        return entityManager.createQuery(
                        """
                        SELECT COUNT(c)
                        FROM BookCopy c
                        WHERE c.book.id = :bookId
                        AND c.status = :status
                        """,
                        Long.class
                )
                .setParameter("bookId", bookId)
                .setParameter(
                        "status",
                        com.smartlibrary.entity.CopyStatus.AVAILABLE
                )
                .getSingleResult();
    }

    // ==========================================
    // RESPONSE MAPPER
    // ==========================================

    private WishlistResponse toResponse(
            Wishlist wishlist
    ) {

        Book book = wishlist.getBook();

        long available = availableCopies(book.getId());

        return new WishlistResponse(

                wishlist.getId(),

                book.getId(),

                book.getTitle(),

                book.getAuthor(),

                book.getIsbn(),

                available,

                available > 0,

                wishlist.getCreatedAt()
        );
    }
}