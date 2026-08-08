package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application
        .TopupSettlementOperations;
import com.storyplatform.monetization.application.port
        .TopupSettlementRepository;
import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.infrastructure
        .TopupSettlementRecoveryWorker;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopupSettlementRecoveryWorkerTest {

    private final TopupSettlementRepository repository =
            mock(TopupSettlementRepository.class);
    private final TopupSettlementOperations settlements =
            mock(TopupSettlementOperations.class);

    @Test
    void replaysQueuedEvidenceUntilQueueIsEmpty() {
        PaymentEvent event = mock(PaymentEvent.class);
        when(event.providerEventId()).thenReturn("event-001");
        when(repository.findOldestReceived("bank-provider"))
                .thenReturn(Optional.of(event), Optional.empty());
        when(settlements.settle("bank-provider", "event-001"))
                .thenReturn(TopupSettlementOperations.Result.CREDITED);

        worker().recover();

        verify(settlements).settle("bank-provider", "event-001");
    }

    @Test
    void stopsWithoutMutationWhileCreditSwitchIsEngaged() {
        PaymentEvent event = mock(PaymentEvent.class);
        when(event.providerEventId()).thenReturn("event-001");
        when(repository.findOldestReceived("bank-provider"))
                .thenReturn(Optional.of(event));
        when(settlements.settle("bank-provider", "event-001"))
                .thenReturn(TopupSettlementOperations.Result.SUSPENDED);

        worker().recover();

        verify(repository).findOldestReceived("bank-provider");
    }

    @Test
    void emptyQueueAndInvalidBatchAreSafe() {
        worker().recover();
        verify(settlements, never()).settle(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        assertThatThrownBy(() -> new TopupSettlementRecoveryWorker(
                repository, settlements, "bank-provider", 0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private TopupSettlementRecoveryWorker worker() {
        return new TopupSettlementRecoveryWorker(
                repository,
                settlements,
                "bank-provider",
                2
        );
    }
}
