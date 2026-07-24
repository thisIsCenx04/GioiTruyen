package com.storyplatform.reading.application;

import com.storyplatform.reading.application.port.ReadingHeartbeatRepository;
import com.storyplatform.reading.application.port.ReadingSessionTokenCodec;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ReadingHeartbeatService
        implements ReadingHeartbeatOperations {

    private static final Duration MAX_EVENT_AGE = Duration.ofMinutes(5);
    private static final Duration MAX_FUTURE_SKEW = Duration.ofSeconds(10);
    private static final Duration MAX_CADENCE = Duration.ofSeconds(60);
    private final ReadingHeartbeatRepository repository;
    private final ReadingSessionTokenCodec tokens;
    private final OutboxAppender outbox;
    private final Clock clock;

    public ReadingHeartbeatService(
            ReadingHeartbeatRepository repository,
            ReadingSessionTokenCodec tokens,
            OutboxAppender outbox,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.tokens = Objects.requireNonNull(tokens);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional
    public HeartbeatReceipt ingest(
            String sessionIdValue,
            String token,
            HeartbeatBatch batch
    ) {
        String sessionId = uuid(sessionIdValue);
        Objects.requireNonNull(batch, "batch");
        String batchId = uuid(batch.batchId());
        Instant now = clock.instant();
        ReadingSessionTokenCodec.Claims claims;
        try {
            claims = tokens.verify(token, now);
        } catch (RuntimeException exception) {
            throw invalid("Reading session token is invalid or expired.");
        }
        if (!sessionId.equals(claims.sessionId())) {
            throw invalid("Reading session token does not match the path.");
        }
        List<Heartbeat> heartbeats = validate(batch.heartbeats(), now);
        long first = heartbeats.getFirst().sequence();
        long last = heartbeats.getLast().sequence();
        var result = repository.apply(
                sessionId,
                claims.actorRef(),
                batchId,
                first - 1,
                last,
                now
        );
        if (result == ReadingHeartbeatRepository.ApplyResult.DUPLICATE) {
            return new HeartbeatReceipt(batchId, last + 1, true);
        }
        if (result == ReadingHeartbeatRepository.ApplyResult.NOT_ACTIVE) {
            throw new ReadingSessionException(
                    "READING_SESSION_NOT_ACTIVE",
                    "Reading session is missing, expired, or complete.",
                    ReadingSessionException.Kind.NOT_FOUND
            );
        }
        if (result
                == ReadingHeartbeatRepository.ApplyResult.SEQUENCE_CONFLICT) {
            throw new ReadingSessionException(
                    "READING_HEARTBEAT_SEQUENCE_CONFLICT",
                    "Heartbeat sequence is stale or has a gap.",
                    ReadingSessionException.Kind.INVALID
            );
        }
        outbox.append(new IntegrationEvent(
                UUID.fromString(batchId),
                "reading.session.heartbeat",
                1,
                now,
                batchId,
                "reading_session",
                sessionId,
                null,
                null,
                new HeartbeatBatchAccepted(
                        sessionId,
                        claims.storyId(),
                        claims.chapterId(),
                        batchId,
                        heartbeats
                )
        ));
        return new HeartbeatReceipt(batchId, last + 1, false);
    }

    private static List<Heartbeat> validate(
            List<Heartbeat> values,
            Instant now
    ) {
        if (values == null || values.isEmpty() || values.size() > 20) {
            throw invalid("Heartbeat batch size must be between 1 and 20.");
        }
        List<Heartbeat> heartbeats = List.copyOf(values);
        Heartbeat previous = null;
        for (Heartbeat heartbeat : heartbeats) {
            if (heartbeat == null || heartbeat.sequence() < 1
                    || heartbeat.sequence() == Long.MAX_VALUE
                    || heartbeat.occurredAt() == null
                    || heartbeat.position() < 0
                    || heartbeat.position() > 100
                    || !Double.isFinite(heartbeat.position())
                    || heartbeat.activeSeconds() < 0
                    || heartbeat.activeSeconds() > 60
                    || heartbeat.occurredAt().isBefore(
                            now.minus(MAX_EVENT_AGE)
                    )
                    || heartbeat.occurredAt().isAfter(
                            now.plus(MAX_FUTURE_SKEW)
                    )) {
                throw invalid("Heartbeat value is outside safe bounds.");
            }
            if (previous != null) {
                Duration cadence = Duration.between(
                        previous.occurredAt(),
                        heartbeat.occurredAt()
                );
                if (heartbeat.sequence() != previous.sequence() + 1
                        || cadence.isNegative()
                        || cadence.isZero()
                        || cadence.compareTo(MAX_CADENCE) > 0) {
                    throw invalid(
                            "Heartbeats must be contiguous and ordered."
                    );
                }
            }
            previous = heartbeat;
        }
        return heartbeats;
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid("A heartbeat identifier is invalid.");
        }
    }

    private static ReadingSessionException invalid(String detail) {
        return new ReadingSessionException(
                "READING_HEARTBEAT_INVALID",
                detail,
                ReadingSessionException.Kind.INVALID
        );
    }

    public record HeartbeatBatchAccepted(
            String sessionId,
            String storyId,
            String chapterId,
            String batchId,
            List<Heartbeat> heartbeats
    ) {
        public HeartbeatBatchAccepted {
            heartbeats = List.copyOf(heartbeats);
        }
    }
}
