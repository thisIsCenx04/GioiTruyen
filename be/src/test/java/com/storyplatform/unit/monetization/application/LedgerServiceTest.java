package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.LedgerConflictException;
import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application.LedgerService;
import com.storyplatform.monetization.application.port.LedgerRepository;
import com.storyplatform.monetization.application.port.LedgerBalanceProjector;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final String TRANSACTION =
            "10000000-0000-4000-8000-000000000001";
    private static final String EVENT =
            "10000000-0000-4000-8000-000000000002";
    private final LedgerRepository repository = mock(LedgerRepository.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final LedgerBalanceProjector balances =
            mock(LedgerBalanceProjector.class);
    private final LedgerService service = new LedgerService(
            repository,
            outbox,
            balances,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new SequentialIds()
    );

    @Test
    void insertsPostingAndAppendsMinimalOutboxEvent() {
        when(repository.findByIdempotencyKeyHash(HASH))
                .thenReturn(Optional.empty());
        when(repository.insert(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );
        ArgumentCaptor<LedgerTransaction> transaction =
                ArgumentCaptor.forClass(LedgerTransaction.class);
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);

        var result = service.post(command(entries(100)));

        assertThat(result.replayed()).isFalse();
        assertThat(result.transaction().createdAt()).isEqualTo(NOW);
        verify(repository).insert(transaction.capture());
        assertThat(transaction.getValue().amountXu()).isEqualTo(100);
        verify(balances).project(transaction.getValue());
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo("monetization.ledger.posted");
        assertThat(event.getValue().payload())
                .isInstanceOf(LedgerService.LedgerPosted.class);
    }

    @Test
    void replaysTheSameKeyWithoutWritingAgain() {
        LedgerTransaction existing = transaction(entries(100));
        when(repository.findByIdempotencyKeyHash(HASH))
                .thenReturn(Optional.of(existing));

        var result = service.post(command(entries(100)));

        assertThat(result.replayed()).isTrue();
        assertThat(result.transaction()).isEqualTo(existing);
        verify(repository, never()).insert(any());
        verify(balances, never()).project(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void rejectsIdempotencyKeyReuseWithDifferentPosting() {
        when(repository.findByIdempotencyKeyHash(HASH))
                .thenReturn(Optional.of(transaction(entries(100))));

        assertThatThrownBy(() -> service.post(command(entries(101))))
                .isInstanceOf(LedgerConflictException.class);
        verify(repository, never()).insert(any());
        verify(balances, never()).project(any());
        verify(outbox, never()).append(any());
    }

    private static LedgerOperations.Command command(
            List<LedgerEntry> entries
    ) {
        return new LedgerOperations.Command(
                LedgerTransaction.Type.DONATION,
                "donation",
                "donation-01",
                entries,
                HASH,
                "correlation-01",
                "actor-01",
                "team-01"
        );
    }

    private static LedgerTransaction transaction(
            List<LedgerEntry> entries
    ) {
        return LedgerTransaction.post(
                TRANSACTION,
                LedgerTransaction.Type.DONATION,
                "donation",
                "donation-01",
                entries,
                HASH,
                NOW
        );
    }

    private static List<LedgerEntry> entries(long amount) {
        return List.of(
                new LedgerEntry(
                        "20000000-0000-4000-8000-000000000001",
                        LedgerEntry.Side.DEBIT,
                        amount
                ),
                new LedgerEntry(
                        "30000000-0000-4000-8000-000000000001",
                        LedgerEntry.Side.CREDIT,
                        amount
                )
        );
    }

    private static final class SequentialIds
            implements java.util.function.Supplier<UUID> {

        private int value = 1;

        @Override
        public UUID get() {
            return UUID.fromString(value++ == 1 ? TRANSACTION : EVENT);
        }
    }
}
