package com.storyplatform.auth.application.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateUserProfileRequest(
        @Size(max = 2000)
        String bio,
        @Size(max = 2048)
        String coverUrl,
        @Size(max = 30)
        String gender,
        LocalDate birthday,
        @Size(max = 2048)
        String websiteUrl
) {
}
