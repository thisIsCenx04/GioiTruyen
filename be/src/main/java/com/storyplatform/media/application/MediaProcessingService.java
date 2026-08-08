package com.storyplatform.media.application;

import com.storyplatform.media.application.port.MediaProcessingGateway;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class MediaProcessingService
        implements MediaProcessingOperations {

    private final Repository repository;
    private final MediaProcessingGateway gateway;
    private final MediaContentValidator validator;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration retryDelay;
    private final int maximumAttempts;

    public MediaProcessingService(
            Repository repository,
            MediaProcessingGateway gateway,
            MediaContentValidator validator,
            Clock clock,
            Duration leaseDuration,
            Duration retryDelay,
            int maximumAttempts
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.retryDelay = positive(retryDelay, "retryDelay");
        if (maximumAttempts < 1 || maximumAttempts > 20) {
            throw new IllegalArgumentException(
                    "maximumAttempts must be between 1 and 20"
            );
        }
        this.maximumAttempts = maximumAttempts;
    }

    @Override
    public boolean processNext(String workerId) {
        String owner = requireWorkerId(workerId);
        var now = clock.instant();
        var claimed = repository.claim(
                owner,
                now,
                now.plus(leaseDuration),
                maximumAttempts
        );
        if (claimed.isEmpty()) {
            return false;
        }
        Candidate candidate = claimed.orElseThrow();
        try {
            byte[] source = gateway.downloadOriginal(
                    candidate,
                    candidate.purpose().maximumBytes()
            );
            validator.validate(candidate, source);
            PublishedAsset published = gateway.publishNormalized(
                    candidate,
                    source
            );
            if (!repository.complete(
                    candidate,
                    owner,
                    published,
                    clock.instant()
            )) {
                throw new IllegalStateException(
                        "media processing lease was lost"
                );
            }
        } catch (MediaPolicyException exception) {
            repository.reject(
                    candidate,
                    owner,
                    exception.code(),
                    clock.instant()
            );
        } catch (RuntimeException exception) {
            repository.reschedule(
                    candidate,
                    owner,
                    safeFailure(exception),
                    clock.instant().plus(retryDelay),
                    candidate.attempt() >= maximumAttempts
            );
        }
        return true;
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String requireWorkerId(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("workerId is invalid");
        }
        return value;
    }

    private static String safeFailure(RuntimeException exception) {
        return exception instanceof MediaGatewayException gateway
                ? gateway.code()
                : "MEDIA_PROCESSING_FAILED";
    }
}
