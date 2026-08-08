package com.storyplatform.teams.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateTeamPermissionsRequest(
        @NotEmpty
        @Size(max = 6)
        Set<@Pattern(
                regexp = "^(story:(create|edit|submit|publish)"
                        + "|analytics:read|finance:request)$"
        ) String> permissions,
        @NotNull @PositiveOrZero Long version
) {
}
