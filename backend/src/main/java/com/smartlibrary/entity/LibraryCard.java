package com.smartlibrary.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "library_cards",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_library_card_user",
                        columnNames = "user_id"
                ),
                @UniqueConstraint(
                        name = "uk_library_card_number",
                        columnNames = "card_number"
                ),
                @UniqueConstraint(
                        name = "uk_library_card_token",
                        columnNames = "verification_token"
                )
        }
)
public class LibraryCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(
            name = "card_number",
            nullable = false,
            length = 50
    )
    private String cardNumber;

    @Column(
            name = "verification_token",
            nullable = false,
            length = 100
    )
    private String verificationToken;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}