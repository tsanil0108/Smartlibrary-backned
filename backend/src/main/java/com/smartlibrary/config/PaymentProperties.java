package com.smartlibrary.config;

import jakarta.validation.constraints.Min;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "library.payment")
public class PaymentProperties {

    private boolean demoEnabled = false;

    @Min(1)
    private long membershipPricePaise = 29900;

    @Min(1)
    private int membershipDays = 30;

    private boolean confirmationEmailEnabled = true;
}