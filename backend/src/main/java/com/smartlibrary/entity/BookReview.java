package com.smartlibrary.entity;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "book_reviews",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_review_user_book",
                        columnNames = {
                                "user_id",
                                "book_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_reviews_book_status",
                        columnList = "book_id,status"
                ),
                @Index(
                        name = "idx_reviews_user",
                        columnList = "user_id"
                )
        }
)
public class BookReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "book_id",
            nullable = false
    )
    private Book book;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status =
            ReviewStatus.PUBLISHED;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @Column(
            name = "moderation_reason",
            length = 500
    )
    private String moderationReason;

    @PrePersist
    void onCreate() {

        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {

        updatedAt = Instant.now();
    }
}