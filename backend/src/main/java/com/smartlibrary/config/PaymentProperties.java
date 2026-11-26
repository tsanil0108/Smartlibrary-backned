package com.smartlibrary.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

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

    // Existing configuration compatibility

    private boolean demoEnabled = false;

    // New payment configuration

    private boolean onlineEnabled = false;

    private boolean couponEnabled = true;

    @Min(1)
    private long membershipPricePaise = 29900;

    @Min(1)
    private int membershipDays = 30;

    private String membershipPlanName =
            "Premium Monthly";

    private String currency = "INR";

    private boolean confirmationEmailEnabled = true;

    @Min(5)
    @Max(120)
    private int orderExpiryMinutes = 15;

    // ==========================================
    // RAZORPAY CONFIGURATION
    // ==========================================

    private String razorpayKeyId = "";

    private String razorpayKeySecret = "";

    private String razorpayWebhookSecret = "";
}