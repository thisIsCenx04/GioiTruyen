package com.storyplatform.system.application.dto;

import com.storyplatform.system.domain.AdEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AdEventRequest(
        UUID userId,
        @Size(max = 255)
        String sessionId,
        UUID storyId,
        @Size(max = 1000)
        String pageUrl,
        @NotNull
        AdEventType eventType
) {
}
