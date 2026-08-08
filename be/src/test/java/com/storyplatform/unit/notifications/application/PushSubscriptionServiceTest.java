package com.storyplatform.unit.notifications.application;

import com.storyplatform.notifications.application.NotificationException;
import com.storyplatform.notifications.application.PushSubscriptionOperations;
import com.storyplatform.notifications.application.PushSubscriptionService;
import com.storyplatform.notifications.application.port
        .PushSubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushSubscriptionServiceTest {

    private static final String USER =
            "10000000-0000-4000-8000-000000000001";
    private static final String ID =
            "20000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private static final String KEY = "A".repeat(43);
    private static final String AUTH = "B".repeat(16);
    private final PushSubscriptionRepository repository =
            mock(PushSubscriptionRepository.class);

    @Test
    void validatesAndRegistersSafeHttpsSubscription() {
        var expected = new PushSubscriptionOperations.SubscriptionView(
                ID, NOW, NOW
        );
        when(repository.save(
                ID,
                USER,
                "https://push.example/subscription",
                KEY,
                AUTH,
                NOW
        )).thenReturn(expected);

        assertThat(service().register(
                USER,
                new PushSubscriptionOperations.SubscriptionCommand(
                        "https://push.example/subscription",
                        KEY,
                        AUTH
                )
        )).isEqualTo(expected);
    }

    @Test
    void rejectsMalformedActorEndpointAndKeys() {
        assertInvalid(() -> service().register(
                "bad",
                new PushSubscriptionOperations.SubscriptionCommand(
                        "https://push.example/subscription", KEY, AUTH
                )
        ));
        assertInvalid(() -> service().register(USER, null));
        for (String endpoint : new String[]{
                "http://push.example/a",
                "https://user@push.example/a",
                "https://push.example/a#fragment",
                "not-a-url"
        }) {
            assertInvalid(() -> service().register(
                    USER,
                    new PushSubscriptionOperations.SubscriptionCommand(
                            endpoint, KEY, AUTH
                    )
            ));
        }
        assertInvalid(() -> service().register(
                USER,
                new PushSubscriptionOperations.SubscriptionCommand(
                        "https://push.example/a", "short", AUTH
                )
        ));
        assertInvalid(() -> service().register(
                USER,
                new PushSubscriptionOperations.SubscriptionCommand(
                        "https://push.example/a", KEY, "bad value!!!!!!!!!"
                )
        ));
    }

    @Test
    void removesOnlyOwnedActiveSubscription() {
        when(repository.remove(ID, USER, NOW)).thenReturn(true, false);
        service().remove(USER, ID);
        verify(repository).remove(ID, USER, NOW);

        assertThatThrownBy(() -> service().remove(USER, ID))
                .isInstanceOf(NotificationException.class)
                .extracting("code")
                .isEqualTo("PUSH_SUBSCRIPTION_NOT_FOUND");
        assertInvalid(() -> service().remove(USER, "bad"));
    }

    private PushSubscriptionService service() {
        return new PushSubscriptionService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> ID
        );
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(NotificationException.class)
                .extracting("code")
                .isEqualTo("PUSH_SUBSCRIPTION_INVALID");
    }
}
