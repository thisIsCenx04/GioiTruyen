package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.TopupRejectionOperations;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RejectTopupRequest(
        @NotNull
        TopupRejectionOperations.ReasonCode reasonCode,
        @NotBlank
        @Size(min = 10, max = 500)
        String reason,
        @NotBlank
        @Size(min = 3, max = 512)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]{2,511}")
        String evidenceReference
) {
}
