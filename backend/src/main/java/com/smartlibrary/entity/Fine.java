package com.smartlibrary.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "fines",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_fine_loan_type",
                        columnNames = {
                                "loan_id",
                                "fine_type"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_fine_user_status",
                        columnList = "user_id,status"
                )
        }
)
public class Fine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Enumerated(EnumType.STRING)
    @Column(name = "fine_type", nullable = false)
    private FineType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FineStatus status = FineStatus.PENDING;

    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal amount;

    @Column(nullable = false)
    private long overdueDays;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    private LocalDateTime waivedAt;

    private String paymentMethod;

    @Column(length = 500)
    private String waiverReason;

    private Long processedByAdminId;
}