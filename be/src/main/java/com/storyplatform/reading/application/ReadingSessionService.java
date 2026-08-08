package com.storyplatform.reading.application;

import com.storyplatform.reading.application.port.ReadingSessionQuota;
import com.storyplatform.reading.application.port.ReadingSessionRepository;
import com.storyplatform.reading.application.port.ReadingSessionTokenCodec;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class ReadingSessionService
        implements ReadingSessionOperations {

    private static final Duration RETENTION = Duration.ofDays(7);
    private final ReadingSessionRepository repository;
    private final ReadingSessionTokenCodec tokens;
    private final ReadingSessionQuota quota;
    private final Clock clock;
    private final Duration ttl;
    private final int heartbeatIntervalSeconds;
    private final Supplier<UUID> identifiers;

    public ReadingSessionService(
            ReadingSessionRepository repository,
            ReadingSessionTokenCodec tokens,
            ReadingSessionQuota quota,
            Clock clock,
            Duration ttl,
            int heartbeatIntervalSeconds,
            Supplier<UUID> identifiers
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.tokens = Objects.requireNonNull(tokens);
        this.quota = Objects.requireNonNull(quota);
        this.clock = Objects.requireNonNull(clock);
        if (ttl == null || ttl.isNegative() || ttl.isZero()
                || ttl.compareTo(Duration.ofHours(2)) > 0
                || heartbeatIntervalSeconds < 5
                || heartbeatIntervalSeconds > 60) {
            throw new IllegalArgumentException(
                    "reading session configuration is invalid"
            );
        }
        this.ttl = ttl;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.identifiers = Objects.requireNonNull(identifiers);
    }

    @Override
    public SessionGrant start(StartCommand command) {
        Objects.requireNonNull(command, "command");
        String storyId = uuid(command.storyId());
        String chapterId = uuid(command.chapterId());
        Actor actor = actor(command);
        String actorRef = tokens.fingerprint(actor.subject());
        try {
            if (!quota.allow(actorRef)) {
                throw new ReadingSessionException(
                        "READING_SESSION_RATE_LIMITED",
                        "Reading session start limit exceeded.",
                        ReadingSessionException.Kind.RATE_LIMITED,
                        quota.retryAfterSeconds()
                );
            }
        } catch (ReadingSessionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ReadingSessionException(
                    "READING_SESSION_QUOTA_UNAVAILABLE",
                    "Reading session abuse protection is unavailable.",
                    ReadingSessionException.Kind.UNAVAILABLE
            );
        }
        if (!repository.chapterIsPublished(storyId, chapterId)) {
            throw new ReadingSessionException(
                    "READING_CHAPTER_NOT_FOUND",
                    "Published story chapter was not found.",
                    ReadingSessionException.Kind.NOT_FOUND
            );
        }
        Instant startedAt = clock.instant();
        Instant expiresAt = startedAt.plus(ttl);
        String sessionId = identifiers.get().toString();
        repository.create(new ReadingSessionRepository.SessionRecord(
                sessionId,
                storyId,
                chapterId,
                actor.type(),
                actorRef,
                startedAt,
                expiresAt,
                expiresAt.plus(RETENTION)
        ));
        String token = tokens.issue(new ReadingSessionTokenCodec.Claims(
                sessionId,
                storyId,
                chapterId,
                actorRef,
                startedAt,
                expiresAt
        ));
        return new SessionGrant(
                sessionId,
                token,
                expiresAt,
                heartbeatIntervalSeconds
        );
    }

    private static Actor actor(StartCommand command) {
        if (command.authenticatedUserId() != null
                && !command.authenticatedUserId().isBlank()) {
            return new Actor(
                    "USER",
                    "user:" + uuid(command.authenticatedUserId())
            );
        }
        if (command.anonymousId() == null
                || command.anonymousId().isBlank()) {
            throw invalid("anonymousId is required for anonymous readers.");
        }
        String address = Objects.toString(
                command.clientAddress(),
                "unknown"
        );
        return new Actor(
                "ANONYMOUS",
                "anonymous:" + uuid(command.anonymousId())
                        + ":address:" + address
        );
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid("A reading session identifier is invalid.");
        }
    }

    private static ReadingSessionException invalid(String detail) {
        return new ReadingSessionException(
                "READING_SESSION_INVALID",
                detail,
                ReadingSessionException.Kind.INVALID
        );
    }

    private record Actor(String type, String subject) {
    }
}
