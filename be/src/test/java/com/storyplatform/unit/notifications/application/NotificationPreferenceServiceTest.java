package com.storyplatform.unit.notifications.application;

import com.storyplatform.notifications.application.NotificationException;
import com.storyplatform.notifications.application
        .NotificationPreferenceOperations;
import com.storyplatform.notifications.application
        .NotificationPreferenceService;
import com.storyplatform.notifications.application.port
        .NotificationPreferenceRepository;
import com.storyplatform.notifications.application.port
        .UnsubscribeTokenCodec;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPreferenceServiceTest {

    private static final String USER =
            "10000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final NotificationPreferenceRepository repository =
            mock(NotificationPreferenceRepository.class);
    private final UnsubscribeTokenCodec tokens =
            mock(UnsubscribeTokenCodec.class);

    @Test
    void defaultsOffAndRequiresExplicitVersionedConsent() {
        var service = service();
        assertThat(service.get(USER))
                .returns(false, value -> value.emailEnabled())
                .returns(false, value -> value.pushEnabled())
                .returns(0L, value -> value.version());

        var command = new NotificationPreferenceOperations.PreferenceCommand(
                true,
                true,
                Set.of("STORY_UPDATES", "COMMUNITY"),
                true
        );
        when(repository.save(any(), org.mockito.ArgumentMatchers.eq(0L)))
                .thenReturn(NotificationPreferenceRepository
                        .SaveOutcome.SUCCESS);
        var updated = service.update(USER, 0, command);

        assertThat(updated.emailEnabled()).isTrue();
        assertThat(updated.consentedAt()).isEqualTo(NOW);
        assertThat(updated.version()).isEqualTo(1);
    }

    @Test
    void rejectsMissingConsentUnknownCategoryAndStaleVersion() {
        assertInvalid(() -> service().update(
                USER,
                0,
                new NotificationPreferenceOperations.PreferenceCommand(
                        true, false, Set.of("STORY_UPDATES"), false
                )
        ));
        assertInvalid(() -> service().update(
                USER,
                0,
                new NotificationPreferenceOperations.PreferenceCommand(
                        false, false, Set.of("UNKNOWN"), false
                )
        ));
        when(repository.save(any(), any(Long.class))).thenReturn(
                NotificationPreferenceRepository.SaveOutcome.CONFLICT
        );
        assertThatThrownBy(() -> service().update(
                USER,
                1,
                new NotificationPreferenceOperations.PreferenceCommand(
                        false, false, Set.of("ACCOUNT"), false
                )
        )).isInstanceOf(NotificationException.class)
                .extracting("code")
                .isEqualTo("NOTIFICATION_PREFERENCE_CONFLICT");
    }

    @Test
    void signedUnsubscribeDisablesOnlySelectedChannelIdempotently() {
        var grant = new UnsubscribeTokenCodec.Grant(
                USER,
                NotificationPreferenceRepository.Channel.EMAIL,
                NOW.plusSeconds(60)
        );
        when(tokens.decode("token")).thenReturn(grant);
        when(repository.disable(
                USER,
                NotificationPreferenceRepository.Channel.EMAIL,
                NOW
        )).thenReturn(NotificationPreferenceRepository.SaveOutcome.SUCCESS);
        when(repository.find(USER)).thenReturn(Optional.of(preference()));

        assertThat(service().unsubscribe("token").emailEnabled()).isFalse();
        verify(repository).disable(
                USER,
                NotificationPreferenceRepository.Channel.EMAIL,
                NOW
        );

        when(tokens.decode("expired")).thenReturn(new UnsubscribeTokenCodec.Grant(
                USER,
                NotificationPreferenceRepository.Channel.EMAIL,
                NOW.minusSeconds(1)
        ));
        assertInvalid(() -> service().unsubscribe("expired"));
    }

    private NotificationPreferenceService service() {
        return new NotificationPreferenceService(
                repository,
                tokens,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static NotificationPreferenceOperations.PreferenceView preference() {
        return new NotificationPreferenceOperations.PreferenceView(
                USER,
                false,
                true,
                Set.of("STORY_UPDATES"),
                NotificationPreferenceService.CONSENT_VERSION,
                NOW,
                NOW,
                2
        );
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(NotificationException.class)
                .extracting("code")
                .isEqualTo("NOTIFICATION_PREFERENCE_INVALID");
    }
}
