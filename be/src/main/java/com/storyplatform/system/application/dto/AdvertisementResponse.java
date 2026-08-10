package com.storyplatform.system.application.dto;

import com.storyplatform.system.domain.AdvertisementPlacement;
import com.storyplatform.system.domain.AdvertisementType;
import java.time.Instant;
import java.util.UUID;

public record AdvertisementResponse(
        UUID id,
        String name,
        AdvertisementType type,
        String imageUrl,
        String targetUrl,
        AdvertisementPlacement placement,
        int cooldownSeconds,
        int maxClicksPerDay,
        int priority,
        Instant startAt,
        Instant endAt,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
