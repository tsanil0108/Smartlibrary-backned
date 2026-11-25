package com.smartlibrary.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "email_reminder_logs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_email_reminder_key",
                        columnNames = "reminder_key"
                )
        }
)
public class EmailReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "reminder_key",
            nullable = false,
            length = 200
    )
    private String reminderKey;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailReminderType type;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private LocalDate reminderDate;

    @Column(nullable = false)
    private boolean sent;

    @Column(nullable = false)
    private int attempts;

    private LocalDateTime sentAt;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}