package com.storyplatform.monetization.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateTopupDiscountRequest(
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal discountPercent,
        @NotBlank
        @Size(min = 10, max = 500)
        String reason
) {
}
