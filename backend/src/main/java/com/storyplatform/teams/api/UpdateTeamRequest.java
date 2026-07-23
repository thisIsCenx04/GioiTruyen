package com.storyplatform.teams.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTeamRequest(
        @NotBlank
        @Size(min = 2, max = 100)
        String name,
        @Size(max = 1000)
        String description,
        @NotNull
        @Min(0)
        Long version
) {
}
