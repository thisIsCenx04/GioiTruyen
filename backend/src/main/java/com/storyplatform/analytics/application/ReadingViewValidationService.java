package com.storyplatform.analytics.application;

import com.storyplatform.analytics.application.port
        .ReadingViewValidationRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReadingViewValidationService
        implements ReadingViewValidationOperations {

    private final ReadingViewValidationRepository repository;
    private final ReadingViewClassifier classifier;
    private final Clock clock;
    private final Duration readinessDelay;
    private final Duration lease;
    private final Duration retryDelay;

    public ReadingViewValidationService(
            ReadingViewValidationRepository repository,
            ReadingViewClassifier classifier,
            Clock clock,
            Duration readinessDelay,
            Duration lease,
            Duration retryDelay
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.readinessDelay = positive(readinessDelay, "readinessDelay");
        this.lease = positive(lease, "lease");
        this.retryDelay = positive(retryDelay, "retryDelay");
    }

    @Override
    public boolean processNext(String workerId) {
        if (workerId == null
                || !workerId.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("workerId is invalid");
        }
        var now = clock.instant();
        var claimed = repository.claim(
                workerId,
                now.minus(readinessDelay),
                now,
                now.plus(lease)
        );
        if (claimed.isEmpty()) {
            return false;
        }
        var bucket = claimed.orElseThrow();
        try {
            repository.save(classify(bucket.events(), now));
            if (!repository.complete(bucket, workerId, clock.instant())) {
                throw new IllegalStateException(
                        "reading validation lease was lost"
                );
            }
        } catch (RuntimeException exception) {
            repository.retry(
                    bucket,
                    workerId,
                    clock.instant().plus(retryDelay),
                    "VALIDATION_FAILED"
            );
        }
        return true;
    }

    private List<ReadingViewClassifier.Classification> classify(
            List<RawReadingEvent> events,
            java.time.Instant now
    ) {
        List<ReadingViewClassifier.Classification> result =
                new ArrayList<>(events.size());
        Map<String, RawReadingEvent> previousBySession = new HashMap<>();
        Map<String, Boolean> selfViews = new HashMap<>();
        for (RawReadingEvent event : events) {
            String fingerprint = fingerprint(event);
            boolean duplicate = repository.claimFingerprint(
                    fingerprint,
                    event.eventId(),
                    now
            );
            RawReadingEvent previous = previousBySession.put(
                    event.sessionRef() + ":" + event.chapterId(),
                    event
            );
            result.add(classifier.classify(
                    new ReadingViewClassifier.Candidate(
                            event.eventId(),
                            fingerprint,
                            event.sessionRef(),
                            event.actorRef(),
                            event.storyId(),
                            event.chapterId(),
                            event.kind(),
                            event.occurredAt(),
                            event.position(),
                            event.activeSeconds(),
                            duplicate,
                            selfViews.computeIfAbsent(
                                    event.storyId()
                                            + ":" + event.actorRef(),
                                    ignored ->
                                            repository.isSelfView(event)
                            ),
                            repository.botSignals(event, previous)
                    ),
                    now
            ));
        }
        return List.copyOf(result);
    }

    private static String fingerprint(RawReadingEvent event) {
        return String.join(
                ":",
                event.sessionRef(),
                event.chapterId(),
                event.kind().name(),
                Long.toString(event.sequence())
        );
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
