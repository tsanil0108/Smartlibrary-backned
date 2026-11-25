package com.smartlibrary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configurable business rules. Nothing here is hard-coded in the services. */
@ConfigurationProperties(prefix = "library")
public record LibraryProperties(
        @DefaultValue("14") int loanDays,
        @DefaultValue("3") int maxActiveLoans,
        @DefaultValue("1") int maxRenewals,
        @DefaultValue("7") int renewalDays,
        @DefaultValue("true") boolean blockOnOverdue,
        @DefaultValue("30") int defaultMembershipDays) {}
