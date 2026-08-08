package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port.EmailVerificationIssuer;
import com.storyplatform.identity.application.port.EmailVerificationRepository;
import com.storyplatform.identity.application.port.VerificationTokenCodec;
import com.storyplatform.identity.domain.EmailVerification;
import com.storyplatform.identity.infrastructure.VerificationProperties;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PersistentEmailVerificationIssuer
        implements EmailVerificationIssuer {

    public static final String EVENT_TYPE = "identity.verification.requested";

    private final EmailVerificationRepository repository;
    private final VerificationTokenCodec tokenCodec;
    private final OutboxAppender outbox;
    private final VerificationProperties properties;

    public PersistentEmailVerificationIssuer(
            EmailVerificationRepository repository,
            VerificationTokenCodec tokenCodec,
            OutboxAppender outbox,
            VerificationProperties properties
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tokenCodec = Objects.requireNonNull(tokenCodec, "tokenCodec");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void issue(
            String userId,
            String correlationId,
            Instant issuedAt
    ) {
        Instant expiresAt = issuedAt.plus(properties.tokenTtl());
        VerificationTokenCodec.IssuedVerificationToken issued =
                tokenCodec.issue(userId, expiresAt);
        EmailVerification verification = EmailVerification.pending(
                issued.verificationId(),
                userId,
                issued.tokenHash(),
                expiresAt,
                issuedAt
        );
        repository.save(verification);
        outbox.append(new IntegrationEvent(
                UUID.fromString(issued.verificationId()),
                EVENT_TYPE,
                1,
                issuedAt,
                correlationId,
                "user",
                userId,
                null,
                null,
                new VerificationRequested(issued.verificationId())
        ));
    }

    public record VerificationRequested(String verificationId) {
    }
}
