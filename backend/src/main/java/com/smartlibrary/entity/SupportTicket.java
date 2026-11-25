package com.smartlibrary.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Locale;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "support_tickets",
        indexes = {
                @Index(
                        name = "idx_ticket_user_status",
                        columnList = "user_id,status"
                ),
                @Index(
                        name = "idx_ticket_status",
                        columnList = "status"
                )
        }
)
public class SupportTicket {

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

    @Column(nullable = false, length = 150)
    private String subject;

    @Column(nullable = false, length = 3000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority =
            TicketPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status =
            TicketStatus.OPEN;

    @Column(name = "assigned_admin_id")
    private Long assignedAdminId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Instant resolvedAt;

    private Instant closedAt;

    @Transient
    public String getTicketNumber() {

        if (id == null) {
            return null;
        }

        return String.format(
                Locale.ROOT,
                "SLM-TKT-%08d",
                id
        );
    }

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