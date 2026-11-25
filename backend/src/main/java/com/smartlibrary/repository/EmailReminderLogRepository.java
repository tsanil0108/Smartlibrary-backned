package com.smartlibrary.repository;

import com.smartlibrary.entity.EmailReminderLog;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailReminderLogRepository
        extends JpaRepository<EmailReminderLog, Long> {

    Optional<EmailReminderLog> findByReminderKey(
            String reminderKey
    );

    boolean existsByReminderKeyAndSentTrue(
            String reminderKey
    );
}