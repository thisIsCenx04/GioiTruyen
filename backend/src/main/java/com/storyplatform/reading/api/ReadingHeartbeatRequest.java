package com.storyplatform.reading.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record ReadingHeartbeatRequest(
        @NotBlank String batchId,
        @NotEmpty @Size(max = 20) List<@Valid Item> heartbeats
) {
    public record Item(
            long sequence,
            Instant occurredAt,
            double position,
            int activeSeconds
    ) {
    }
}
