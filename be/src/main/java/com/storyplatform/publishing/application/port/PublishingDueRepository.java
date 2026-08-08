package com.storyplatform.publishing.application.port;

import com.storyplatform.publishing.domain.PublishingSchedule;

import java.time.Instant;
import java.util.Optional;

public interface PublishingDueRepository {

    Optional<PublishingSchedule> claim(
            String workerId,
            Instant now,
            Instant leaseUntil
    );

    boolean defer(
            PublishingSchedule schedule,
            String workerId,
            Instant retryAt,
            Instant updatedAt
    );

    boolean publish(
            PublishingSchedule schedule,
            String workerId,
            Instant publishedAt
    );
}
