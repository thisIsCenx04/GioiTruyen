package com.storyplatform.identity.application;

import com.storyplatform.identity.application.port.PasswordResetRepository;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.application.port.VerificationTokenCodec;
import com.storyplatform.identity.domain.EmailNormalizer;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public final class RequestPasswordResetUseCase {

    public static final String EVENT_TYPE = "identity.password.reset.requested";

    private final UserAccountRepository users;
    private final PasswordResetRepository resets;
    private final VerificationTokenCodec tokens;
    private final EmailNormalizer emails;
    private final OutboxAppender outbox;
    private final Duration tokenTtl;
    private final Clock clock;

    public RequestPasswordResetUseCase(
            UserAccountRepository users,
            PasswordResetRepository resets,
            VerificationTokenCodec tokens,
            EmailNormalizer emails,
            OutboxAppender outbox,
            Duration tokenTtl,
            Clock clock
    ) {
        this.users = Objects.requireNonNull(users, "users");
        this.resets = Objects.requireNonNull(resets, "resets");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.emails = Objects.requireNonNull(emails, "emails");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.tokenTtl = Objects.requireNonNull(tokenTtl, "tokenTtl");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void request(String rawEmail, String correlationId) {
        String email;
        try {
            email = emails.normalize(rawEmail);
        } catch (RuntimeException exception) {
            return;
        }
        users.findByEmail(email)
                .filter(UserAccount::isActive)
                .ifPresent(user -> issue(user.id(), correlationId));
    }

    private void issue(String userId, String correlationId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(tokenTtl);
        VerificationTokenCodec.IssuedVerificationToken issued =
                tokens.issue(userId, expiresAt);
        resets.save(new PasswordResetRepository.PasswordReset(
                issued.verificationId(),
                userId,
                issued.tokenHash(),
                expiresAt,
                null,
                now
        ));
        outbox.append(new IntegrationEvent(
                UUID.fromString(issued.verificationId()),
                EVENT_TYPE,
                1,
                now,
                correlationId,
                "user",
                userId,
                null,
                null,
                new PasswordResetRequested(issued.verificationId())
        ));
    }

    public record PasswordResetRequested(String resetId) {
    }
}
