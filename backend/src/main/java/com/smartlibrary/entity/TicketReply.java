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
        name = "ticket_replies",
        indexes = {
                @Index(
                        name = "idx_reply_ticket",
                        columnList = "ticket_id,created_at"
                )
        }
)
public class TicketReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "ticket_id",
            nullable = false
    )
    private SupportTicket ticket;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "sender_id",
            nullable = false
    )
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role senderRole;

    @Column(nullable = false, length = 3000)
    private String message;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @PrePersist
    void onCreate() {

        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}