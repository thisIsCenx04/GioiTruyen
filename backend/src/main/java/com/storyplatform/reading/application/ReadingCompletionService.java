package com.storyplatform.reading.application;

import com.storyplatform.reading.application.port.ReadingCompletionRepository;
import com.storyplatform.reading.application.port.ReadingSessionTokenCodec;
import com.storyplatform.reading.application.contract.ReadingSessionEvents;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ReadingCompletionService
        implements ReadingCompletionOperations {

    private static final Duration FUTURE_SKEW = Duration.ofSeconds(10);
    private final ReadingCompletionRepository repository;
    private final ReadingSessionTokenCodec tokens;
    private final OutboxAppender outbox;
    private final Clock clock;

    public ReadingCompletionService(
            ReadingCompletionRepository repository,
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
    public CompletionReceipt complete(
            String sessionIdValue,
            String token,
            CompletionCommand command
    ) {
        String sessionId = uuid(sessionIdValue);
        Objects.requireNonNull(command, "command");
        String completionId = uuid(command.completionId());
        Instant now = clock.instant();
        ReadingSessionTokenCodec.Claims claims;
        try {
            claims = tokens.verify(token, now);
        } catch (RuntimeException exception) {
            throw invalid("Reading session token is invalid or expired.");
        }
        if (!sessionId.equals(claims.sessionId())
                || command.finalSequence() < 0
                || command.occurredAt() == null
                || command.occurredAt().isBefore(claims.issuedAt())
                || command.occurredAt().isAfter(claims.expiresAt())
                || command.occurredAt().isAfter(now.plus(FUTURE_SKEW))
                || command.position() < 0
                || command.position() > 100
                || !Double.isFinite(command.position())) {
            throw invalid("Reading completion is outside safe bounds.");
        }
        var result = repository.complete(
                sessionId,
                claims.actorRef(),
                completionId,
                command.finalSequence(),
                command.occurredAt(),
                now
        );
        if (result
                == ReadingCompletionRepository.CompleteResult.DUPLICATE) {
            return new CompletionReceipt(
                    completionId,
                    "COMPLETION_PENDING",
                    true
            );
        }
        if (result
                == ReadingCompletionRepository.CompleteResult.NOT_ACTIVE) {
            throw new ReadingSessionException(
                    "READING_SESSION_NOT_ACTIVE",
                    "Reading session is missing, expired, or complete.",
                    ReadingSessionException.Kind.NOT_FOUND
            );
        }
        if (result == ReadingCompletionRepository.CompleteResult
                .SEQUENCE_CONFLICT) {
            throw invalid(
                    "Final sequence does not match accepted heartbeats."
            );
        }
        outbox.append(new IntegrationEvent(
                UUID.fromString(completionId),
                ReadingSessionEvents.COMPLETED,
                1,
                now,
                completionId,
                "reading_session",
                sessionId,
                null,
                null,
                new ReadingSessionEvents.ReadingSessionCompleted(
                        sessionId,
                        claims.storyId(),
                        claims.chapterId(),
                        completionId,
                        command.finalSequence(),
                        command.occurredAt(),
                        command.position()
                )
        ));
        return new CompletionReceipt(
                completionId,
                "COMPLETION_PENDING",
                false
        );
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid("A reading completion identifier is invalid.");
        }
    }

    private static ReadingSessionException invalid(String detail) {
        return new ReadingSessionException(
                "READING_COMPLETION_INVALID",
                detail,
                ReadingSessionException.Kind.INVALID
        );
    }

}
