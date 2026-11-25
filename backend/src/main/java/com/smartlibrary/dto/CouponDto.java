package com.smartlibrary.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class CouponDto {

    public record Create(

            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9_-]{3,40}")
            String code,

            @Min(1)
            @Max(100)
            int percent,

            @Min(1)
            int maxUses,

            @NotNull
            LocalDate expiresOn

    ) {}

    public record Checkout(

            @NotBlank
            @Size(max = 40)
            String code

    ) {}

    public record Quote(

            String code,
            long originalPaise,
            long discountPaise,
            long payablePaise,
            boolean demo

    ) {}
}