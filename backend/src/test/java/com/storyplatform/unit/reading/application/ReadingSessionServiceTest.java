package com.storyplatform.unit.reading.application;

import com.storyplatform.reading.application.ReadingSessionException;
import com.storyplatform.reading.application.ReadingSessionOperations;
import com.storyplatform.reading.application.ReadingSessionService;
import com.storyplatform.reading.application.port.ReadingSessionQuota;
import com.storyplatform.reading.application.port.ReadingSessionRepository;
import com.storyplatform.reading.application.port.ReadingSessionTokenCodec;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingSessionServiceTest {

    private static final String USER =
            "10000000-0000-4000-8000-000000000001";
    private static final String ANONYMOUS =
            "10000000-0000-4000-8000-000000000002";
    private static final String STORY =
            "20000000-0000-4000-8000-000000000001";
    private static final String CHAPTER =
            "30000000-0000-4000-8000-000000000001";
    private static final UUID SESSION = UUID.fromString(
            "40000000-0000-4000-8000-000000000001"
    );
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final ReadingSessionRepository repository =
            mock(ReadingSessionRepository.class);
    private final ReadingSessionTokenCodec tokens =
            mock(ReadingSessionTokenCodec.class);
    private final ReadingSessionQuota quota =
            mock(ReadingSessionQuota.class);

    @Test
    void startsAnAuthenticatedSessionWithoutPersistingRawIdentity() {
        when(tokens.fingerprint("user:" + USER))
                .thenReturn("opaque-actor");
        when(quota.allow("opaque-actor")).thenReturn(true);
        when(repository.chapterIsPublished(STORY, CHAPTER))
                .thenReturn(true);
        when(tokens.issue(org.mockito.ArgumentMatchers.any()))
                .thenReturn("signed-token");

        var grant = service().start(command(null, USER, "10.0.0.1"));

        assertThat(grant.sessionId()).isEqualTo(SESSION.toString());
        assertThat(grant.sessionToken()).isEqualTo("signed-token");
        assertThat(grant.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(grant.heartbeatIntervalSeconds()).isEqualTo(15);
        verify(repository).create(
                new ReadingSessionRepository.SessionRecord(
                        SESSION.toString(),
                        STORY,
                        CHAPTER,
                        "USER",
                        "opaque-actor",
                        NOW,
                        NOW.plusSeconds(1800),
                        NOW.plusSeconds(1800)
                                .plus(Duration.ofDays(7))
                )
        );
    }

    @Test
    void bindsAnonymousQuotaToAnonymousIdAndAddressBeforeHashing() {
        when(tokens.fingerprint(contains(
                "anonymous:" + ANONYMOUS + ":address:10.0.0.2"
        ))).thenReturn("opaque-anonymous");
        when(quota.allow("opaque-anonymous")).thenReturn(true);
        when(repository.chapterIsPublished(STORY, CHAPTER))
                .thenReturn(true);
        when(tokens.issue(org.mockito.ArgumentMatchers.any()))
                .thenReturn("signed-token");

        service().start(command(ANONYMOUS, null, "10.0.0.2"));

        verify(tokens).fingerprint(
                "anonymous:" + ANONYMOUS + ":address:10.0.0.2"
        );
    }

    @Test
    void rejectsMissingAnonymousIdentityQuotaAndHiddenChapters() {
        assertThatThrownBy(() -> service().start(
                command(null, null, "10.0.0.1")
        )).isInstanceOf(ReadingSessionException.class);

        when(tokens.fingerprint("user:" + USER))
                .thenReturn("opaque-actor");
        when(quota.allow("opaque-actor")).thenReturn(false);
        when(quota.retryAfterSeconds()).thenReturn(60L);
        assertThatThrownBy(() -> service().start(
                command(null, USER, "10.0.0.1")
        )).isInstanceOf(ReadingSessionException.class)
                .extracting(error -> ((ReadingSessionException) error)
                        .retryAfterSeconds())
                .isEqualTo(60L);

        when(quota.allow("opaque-actor")).thenReturn(true);
        when(repository.chapterIsPublished(STORY, CHAPTER))
                .thenReturn(false);
        assertThatThrownBy(() -> service().start(
                command(null, USER, "10.0.0.1")
        )).isInstanceOf(ReadingSessionException.class);
    }

    @Test
    void failsClosedWhenQuotaIsUnavailableAndValidatesConfiguration() {
        when(tokens.fingerprint("user:" + USER))
                .thenReturn("opaque-actor");
        when(quota.allow("opaque-actor"))
                .thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> service().start(
                command(null, USER, null)
        )).isInstanceOf(ReadingSessionException.class)
                .extracting(error -> ((ReadingSessionException) error)
                        .kind())
                .isEqualTo(ReadingSessionException.Kind.UNAVAILABLE);
        assertThatThrownBy(() -> new ReadingSessionService(
                repository,
                tokens,
                quota,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ZERO,
                15,
                () -> SESSION
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private ReadingSessionService service() {
        return new ReadingSessionService(
                repository,
                tokens,
                quota,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(30),
                15,
                () -> SESSION
        );
    }

    private static ReadingSessionOperations.StartCommand command(
            String anonymousId,
            String userId,
            String address
    ) {
        return new ReadingSessionOperations.StartCommand(
                STORY,
                CHAPTER,
                anonymousId,
                userId,
                address
        );
    }
}
