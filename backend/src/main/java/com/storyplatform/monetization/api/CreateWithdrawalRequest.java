package com.storyplatform.monetization.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateWithdrawalRequest(
        @Min(100_000)
        @Max(1_000_000_000)
        long grossAmountXu,
        @NotBlank
        @Pattern(regexp =
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}"
                        + "-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        String destinationId
) {
}
