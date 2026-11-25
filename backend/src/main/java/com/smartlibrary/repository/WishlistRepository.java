package com.smartlibrary.repository;

import com.smartlibrary.entity.Wishlist;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface WishlistRepository
        extends JpaRepository<Wishlist, Long> {

    @EntityGraph(attributePaths = {"book"})
    List<Wishlist> findByUser_IdOrderByCreatedAtDesc(
            Long userId
    );

    Optional<Wishlist> findByUser_IdAndBook_Id(
            Long userId,
            Long bookId
    );

    boolean existsByUser_IdAndBook_Id(
            Long userId,
            Long bookId
    );

    long countByUser_Id(Long userId);

    long deleteByUser_IdAndBook_Id(
            Long userId,
            Long bookId
    );
}