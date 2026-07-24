package com.storyplatform.monetization.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateTopupRequest(
        @Min(10_000)
        @Max(1_000_000_000)
        long amountVnd
) {
}
