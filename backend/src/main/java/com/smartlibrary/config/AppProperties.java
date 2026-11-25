package com.smartlibrary.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("Asia/Kolkata") String timezone,
        Jwt jwt,
        @DefaultValue Cors cors,
        @DefaultValue Seed seed) {

    public record Jwt(String secret, @DefaultValue("120") long expirationMinutes) {}

    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {}

    public record Seed(@DefaultValue("true") boolean enabled,
                       @DefaultValue("admin@library.com") String adminEmail,
                       @DefaultValue("Admin@12345") String adminPassword) {}
}
