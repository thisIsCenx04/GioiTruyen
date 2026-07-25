package com.storyplatform.monetization.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateMonetizationKillSwitchRequest(
        @NotNull Boolean engaged,
        @Size(min = 10, max = 500) String reason
) {
}
