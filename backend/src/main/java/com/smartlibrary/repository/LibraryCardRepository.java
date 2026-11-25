package com.smartlibrary.repository;

import com.smartlibrary.entity.LibraryCard;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryCardRepository
        extends JpaRepository<LibraryCard, Long> {

    Optional<LibraryCard> findByUser_Id(
            Long userId
    );

    Optional<LibraryCard> findByCardNumber(
            String cardNumber
    );

    Optional<LibraryCard> findByVerificationToken(
            String verificationToken
    );

    boolean existsByCardNumber(
            String cardNumber
    );
}