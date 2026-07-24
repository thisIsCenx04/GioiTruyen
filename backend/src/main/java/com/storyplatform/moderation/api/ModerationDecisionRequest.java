package com.storyplatform.moderation.api;

import com.storyplatform.moderation.application
        .ModerationDecisionOperations;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ModerationDecisionRequest(
        @NotNull ModerationDecisionOperations.Decision decision,
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$")
        String reasonCode,
        @Size(max = 2000) String note,
        @Size(max = 20)
        List<
                @Pattern(
                        regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"
                )
                String
                > evidenceRefs,
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[a-z0-9][a-z0-9._-]+$")
        String policyVersion
) {
}
