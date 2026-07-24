package com.storyplatform.monetization.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewWithdrawalRequest(
        @NotBlank
        @Size(min = 10, max = 500)
        String reason
) {
}
