package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.TopupDiscountException;
import com.storyplatform.monetization.application.TopupDiscountService;
import com.storyplatform.monetization.application.port
        .ConfigurationChangeAuthorizer;
import com.storyplatform.monetization.application.port
        .TopupDiscountRepository;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopupDiscountServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");
    private final TopupDiscountRepository repository =
            mock(TopupDiscountRepository.class);
    private final ConfigurationChangeAuthorizer authorizer =
            mock(ConfigurationChangeAuthorizer.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final TopupDiscountService service = new TopupDiscountService(
            repository,
            authorizer,
            outbox,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> UUID.fromString(
                    "10000000-0000-4000-8000-000000000001"
            )
    );

    @Test
    void returnsAuditableTenPercentDefault() {
        when(repository.current()).thenReturn(Optional.empty());

        var current = service.current();

        assertThat(current.discountPercent()).isEqualByComparingTo("10");
        assertThat(current.version()).isZero();
        assertThat(current.changedBy()).isEqualTo("system-default");
    }

    @Test
    void insertsNextVersionAfterScopedReauthentication() {
        when(repository.current()).thenReturn(Optional.empty());
        when(authorizer.consume("admin", "grant")).thenReturn(true);
        when(repository.insert(any(), any(), any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );
        ArgumentCaptor<TopupDiscountRepository.VersionedDiscount> config =
                ArgumentCaptor.forClass(
                        TopupDiscountRepository.VersionedDiscount.class
                );
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);

        var updated = service.update(
                "admin",
                "grant",
                0,
                new BigDecimal("12.50"),
                "Seasonal operating policy"
        );

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.discountPercent()).isEqualByComparingTo("12.50");
        verify(repository).insert(
                config.capture(),
                org.mockito.ArgumentMatchers.eq(BigDecimal.TEN),
                org.mockito.ArgumentMatchers.eq(
                        "Seasonal operating policy"
                )
        );
        assertThat(config.getValue().effectiveAt()).isEqualTo(NOW);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo("monetization.topupdiscount.changed");
    }

    @Test
    void rejectsStaleInvalidAndUnreauthenticatedChanges() {
        when(repository.current()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                "admin", "grant", 1, BigDecimal.TEN, "A valid reason"
        )).isInstanceOf(TopupDiscountException.class)
                .extracting("kind")
                .isEqualTo(TopupDiscountException.Kind.CONFLICT);
        assertThatThrownBy(() -> service.update(
                "admin", "grant", 0,
                new BigDecimal("10.001"), "A valid reason"
        )).isInstanceOf(TopupDiscountException.class)
                .extracting("kind")
                .isEqualTo(TopupDiscountException.Kind.INVALID);
        assertThatThrownBy(() -> service.update(
                "admin", "grant", 0, BigDecimal.TEN, "A valid reason"
        )).isInstanceOf(TopupDiscountException.class)
                .extracting("kind")
                .isEqualTo(TopupDiscountException.Kind.FORBIDDEN);
        verify(repository, never()).insert(any(), any(), any());
        verify(outbox, never()).append(any());
    }
}
