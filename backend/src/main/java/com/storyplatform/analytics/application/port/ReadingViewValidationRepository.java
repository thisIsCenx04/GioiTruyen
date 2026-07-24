package com.storyplatform.analytics.application.port;

import com.storyplatform.analytics.application.RawReadingEvent;
import com.storyplatform.analytics.application.ReadingViewClassifier;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ReadingViewValidationRepository {

    Optional<ClaimedBucket> claim(
            String workerId,
            Instant readyBefore,
            Instant now,
            Instant leaseUntil
    );

    boolean claimFingerprint(String fingerprint, String eventId, Instant now);

    boolean isSelfView(RawReadingEvent event);

    Set<String> botSignals(
            RawReadingEvent event,
            RawReadingEvent previous
    );

    void save(List<ReadingViewClassifier.Classification> classifications);

    boolean complete(
            ClaimedBucket bucket,
            String workerId,
            Instant now
    );

    void retry(
            ClaimedBucket bucket,
            String workerId,
            Instant retryAt,
            String failureCode
    );

    record ClaimedBucket(
            String id,
            int validatedCount,
            int snapshotCount,
            List<RawReadingEvent> events
    ) {
        public ClaimedBucket {
            events = List.copyOf(events);
        }
    }
}
