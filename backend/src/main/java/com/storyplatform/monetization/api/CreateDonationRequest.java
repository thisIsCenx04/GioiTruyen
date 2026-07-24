package com.storyplatform.monetization.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDonationRequest(
        @NotBlank
        @Size(max = 128)
        String teamId,
        @Min(1)
        @Max(1_000_000_000)
        long amountXu,
        @Size(max = 500)
        String message
) {
}
