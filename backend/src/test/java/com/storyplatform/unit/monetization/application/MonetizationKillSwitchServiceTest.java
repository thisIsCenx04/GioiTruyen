package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application
        .MonetizationKillSwitchException;
import com.storyplatform.monetization.application
        .MonetizationKillSwitchGuard;
import com.storyplatform.monetization.application
        .MonetizationKillSwitchService;
import com.storyplatform.monetization.application
        .MonetizationSuspendedException;
import com.storyplatform.monetization.application.port
        .MonetizationKillSwitchAuthorizer;
import com.storyplatform.monetization.application.port
        .MonetizationKillSwitchRepository;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonetizationKillSwitchServiceTest {

    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");
    private final MonetizationKillSwitchRepository repository =
            mock(MonetizationKillSwitchRepository.class);
    private final MonetizationKillSwitchAuthorizer authorizer =
            mock(MonetizationKillSwitchAuthorizer.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);

    @Test
    void defaultsOpenAndAuditsVersionedEngagement() {
        when(authorizer.consume(
                ACTOR,
                "reauth",
                MonetizationKillSwitch.Operation.TOPUP_CREDIT
        )).thenReturn(true);
        when(repository.save(any(), any(), any())).thenReturn(true);

        assertThat(service().current())
                .hasSize(MonetizationKillSwitch.Operation.values().length)
                .allSatisfy(value -> {
                    assertThat(value.engaged()).isFalse();
                    assertThat(value.version()).isZero();
                });
        var updated = service().update(
                ACTOR,
                "reauth",
                MonetizationKillSwitch.Operation.TOPUP_CREDIT,
                0,
                true,
                "Incident containment approved."
        );

        assertThat(updated.engaged()).isTrue();
        assertThat(updated.version()).isOne();
        verify(repository).save(
                any(), any(), org.mockito.ArgumentMatchers
                        .eq("Incident containment approved.")
        );
        verify(outbox).append(any());
    }

    @Test
    void rejectsStaleUnauthorizedAndConcurrentChanges() {
        var current = state(true, 2);
        when(repository.find(
                MonetizationKillSwitch.Operation.TOPUP_CREDIT
        )).thenReturn(Optional.of(current));

        assertKind(() -> service().update(
                ACTOR, "reauth",
                MonetizationKillSwitch.Operation.TOPUP_CREDIT,
                1, false, "Restore after incident review."
        ), MonetizationKillSwitchException.Kind.CONFLICT);
        assertKind(() -> service().update(
                ACTOR, "reauth",
                MonetizationKillSwitch.Operation.TOPUP_CREDIT,
                2, false, "Restore after incident review."
        ), MonetizationKillSwitchException.Kind.FORBIDDEN);

        when(authorizer.consume(any(), any(), any())).thenReturn(true);
        when(repository.save(any(), any(), any())).thenReturn(false);
        assertKind(() -> service().update(
                ACTOR, "reauth",
                MonetizationKillSwitch.Operation.TOPUP_CREDIT,
                2, false, "Restore after incident review."
        ), MonetizationKillSwitchException.Kind.CONFLICT);
    }

    @Test
    void guardFailsClosedOnlyForPersistedEngagedOperation() {
        when(repository.find(
                MonetizationKillSwitch.Operation.WITHDRAWAL_REQUEST
        )).thenReturn(Optional.of(state(true, 1)));
        var guard = new MonetizationKillSwitchGuard(repository);

        assertThat(guard.engaged(
                MonetizationKillSwitch.Operation.TOPUP_CREDIT
        )).isFalse();
        assertThatThrownBy(() -> guard.requireOpen(
                MonetizationKillSwitch.Operation.WITHDRAWAL_REQUEST
        )).isInstanceOf(MonetizationSuspendedException.class);
    }

    private MonetizationKillSwitchService service() {
        return new MonetizationKillSwitchService(
                repository,
                authorizer,
                outbox,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> UUID.fromString(
                        "20000000-0000-4000-8000-000000000001"
                )
        );
    }

    private static MonetizationKillSwitch state(
            boolean engaged,
            long version
    ) {
        return new MonetizationKillSwitch(
                MonetizationKillSwitch.Operation.TOPUP_CREDIT,
                engaged,
                version,
                ACTOR,
                NOW
        );
    }

    private static void assertKind(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            MonetizationKillSwitchException.Kind kind
    ) {
        assertThatThrownBy(action)
                .isInstanceOf(MonetizationKillSwitchException.class)
                .extracting("kind")
                .isEqualTo(kind);
    }
}
