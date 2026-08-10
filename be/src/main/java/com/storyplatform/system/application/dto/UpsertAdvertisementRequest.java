package com.storyplatform.system.application.dto;

import com.storyplatform.system.domain.AdvertisementPlacement;
import com.storyplatform.system.domain.AdvertisementType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpsertAdvertisementRequest(
        @NotBlank
        @Size(max = 180)
        String name,
        @NotNull
        AdvertisementType type,
        @Size(max = 2048)
        String imageUrl,
        @NotBlank
        @Size(max = 2048)
        String targetUrl,
        @NotNull
        AdvertisementPlacement placement,
        @Min(0)
        @Max(86400)
        int cooldownSeconds,
        @Min(0)
        @Max(100)
        int maxClicksPerDay,
        @Min(0)
        int priority,
        Instant startAt,
        Instant endAt,
        boolean active
) {
}
