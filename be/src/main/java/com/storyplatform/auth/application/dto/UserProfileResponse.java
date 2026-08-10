package com.storyplatform.auth.application.dto;

import java.time.Instant;
import java.time.LocalDate;

public record UserProfileResponse(
        String bio,
        String coverUrl,
        String gender,
        LocalDate birthday,
        String websiteUrl,
        Instant updatedAt
) {
}
