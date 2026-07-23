package com.storyplatform.teams.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
        @Size(min = 3, max = 50)
        String slug,
        @NotBlank
        @Size(min = 2, max = 100)
        String name,
        @Size(max = 1000)
        String description
) {
}
