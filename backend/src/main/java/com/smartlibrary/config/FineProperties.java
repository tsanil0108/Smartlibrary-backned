package com.smartlibrary.config;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;

import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "library.fine")
public class FineProperties {

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal overduePerDay =
            new BigDecimal("5.00");

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal damagedCharge =
            new BigDecimal("200.00");

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal lostCharge =
            new BigDecimal("500.00");

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal maxOverdueFine =
            new BigDecimal("500.00");

    private boolean blockBorrowingOnPendingFine = true;
}