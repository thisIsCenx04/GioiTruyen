package com.storyplatform.unit.identity.application;

import com.storyplatform.identity.application.RequestPasswordResetUseCase;
import com.storyplatform.identity.application.port.PasswordResetRepository;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.application.port.VerificationTokenCodec;
import com.storyplatform.identity.domain.EmailNormalizer;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestPasswordResetUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final UserAccountRepository users =
            mock(UserAccountRepository.class);
    private final PasswordResetRepository resets =
            mock(PasswordResetRepository.class);
    private final VerificationTokenCodec tokens =
            mock(VerificationTokenCodec.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final RequestPasswordResetUseCase useCase =
            new RequestPasswordResetUseCase(
                    users,
                    resets,
                    tokens,
                    new EmailNormalizer(),
                    outbox,
                    Duration.ofHours(1),
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void activeAccountCreatesHashedResetAndOutboxEvent() {
        UserAccount account = mock(UserAccount.class);
        when(account.isActive()).thenReturn(true);
        when(account.id()).thenReturn("user-1");
        when(users.findByEmail("reader@example.com"))
                .thenReturn(Optional.of(account));
        when(tokens.issue("user-1", NOW.plusSeconds(3600)))
                .thenReturn(
                        new VerificationTokenCodec.IssuedVerificationToken(
                                "34e591ae-79b1-4da3-a5ce-d1e902499535",
                                "token-hash"
                        )
                );

        useCase.request(" Reader@Example.COM ", "correlation-1");

        verify(resets).save(any());
        verify(outbox).append(any());
    }

    @Test
    void unknownAndInvalidEmailsHaveNoObservableSideEffects() {
        when(users.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        useCase.request("unknown@example.com", "correlation-1");
        useCase.request("invalid", "correlation-1");

        verify(resets, never()).save(any());
        verify(outbox, never()).append(any());
    }
}
