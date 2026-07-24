package com.storyplatform.unit.analytics.infrastructure;

import com.storyplatform.analytics.application.ViewAggregateOperations;
import com.storyplatform.analytics.application.port.ViewAggregateRepository;
import com.storyplatform.analytics.infrastructure
        .TransactionalViewAggregateRepository;
import com.storyplatform.analytics.infrastructure.ViewAggregateWorker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViewAggregateInfrastructureTest {

    @Test
    void transactionalDecoratorDelegatesEveryOperation() {
        ViewAggregateRepository delegate =
                mock(ViewAggregateRepository.class);
        var candidate = candidate();
        var delta = new ViewAggregateRepository.Delta(
                1, 1, 1, 0, Set.of()
        );
        when(delegate.claim(
                "worker_01", Instant.EPOCH, Instant.EPOCH.plusSeconds(1)
        )).thenReturn(Optional.of(candidate));
        when(delegate.commit(
                candidate, "worker_01", delta, Instant.EPOCH
        )).thenReturn(true);
        var repository = new TransactionalViewAggregateRepository(delegate);

        assertThat(repository.claim(
                "worker_01", Instant.EPOCH, Instant.EPOCH.plusSeconds(1)
        )).contains(candidate);
        assertThat(repository.commit(
                candidate, "worker_01", delta, Instant.EPOCH
        )).isTrue();
        repository.retry(
                candidate, "worker_01", Instant.EPOCH.plusSeconds(2)
        );
        repository.reset("story", Instant.EPOCH, Instant.EPOCH.plusSeconds(3));
        repository.reconcile(
                "story", Instant.EPOCH, Instant.EPOCH.plusSeconds(3)
        );
        verify(delegate).retry(
                candidate, "worker_01", Instant.EPOCH.plusSeconds(2)
        );
        verify(delegate).reset(
                "story", Instant.EPOCH, Instant.EPOCH.plusSeconds(3)
        );
        verify(delegate).reconcile(
                "story", Instant.EPOCH, Instant.EPOCH.plusSeconds(3)
        );
    }

    @Test
    void workerStopsOnEmptyQueueAndBoundsBatch() {
        ViewAggregateOperations empty = mock(ViewAggregateOperations.class);
        when(empty.processNext(anyString())).thenReturn(false);
        new ViewAggregateWorker(empty).poll();
        verify(empty).processNext(anyString());

        ViewAggregateOperations busy = mock(ViewAggregateOperations.class);
        when(busy.processNext(anyString())).thenReturn(true);
        new ViewAggregateWorker(busy).poll();
        verify(busy, times(100)).processNext(anyString());
    }

    private static ViewAggregateRepository.Candidate candidate() {
        return new ViewAggregateRepository.Candidate(
                "classification",
                "story",
                "COMPLETION",
                Instant.EPOCH,
                true,
                Set.of(),
                "PASS"
        );
    }
}
