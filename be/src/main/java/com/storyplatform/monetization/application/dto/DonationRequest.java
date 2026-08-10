package com.storyplatform.monetization.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DonationRequest(
        UUID storyId,
        @Min(1)
        long coinAmount,
        @Size(max = 500)
        String message
) {
}
