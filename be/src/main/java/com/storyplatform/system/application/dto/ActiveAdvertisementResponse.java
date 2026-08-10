package com.storyplatform.system.application.dto;

import java.util.UUID;

public record ActiveAdvertisementResponse(
        UUID id,
        String targetUrl,
        int cooldownSeconds,
        int maxClicksPerDay
) {
}
