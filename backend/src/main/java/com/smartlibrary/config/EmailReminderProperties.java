package com.smartlibrary.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "library.reminders")
public class EmailReminderProperties {

    private boolean enabled = true;

    private String sender;

    private String zone = "Asia/Kolkata";

    private String cron = "0 0 9 * * *";

    private int dueBeforeDays = 3;

    private int membershipBeforeDays = 7;
}