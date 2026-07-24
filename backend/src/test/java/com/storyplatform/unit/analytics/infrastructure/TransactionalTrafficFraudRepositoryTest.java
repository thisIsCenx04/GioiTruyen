package com.storyplatform.unit.analytics.infrastructure;

import com.storyplatform.analytics.application.TrafficFraudScorer;
import com.storyplatform.analytics.application.port.TrafficFraudRepository;
import com.storyplatform.analytics.infrastructure
        .TransactionalTrafficFraudRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionalTrafficFraudRepositoryTest {

    @Test
    void delegatesClaimCommitAndRetry() {
        TrafficFraudRepository delegate =
                mock(TrafficFraudRepository.class);
        var candidate = new TrafficFraudRepository.Candidate(
                "id", "event", "actor", "story", Set.of()
        );
        var score = new TrafficFraudScorer.Score(
                TrafficFraudScorer.RULE_VERSION,
                0,
                TrafficFraudScorer.Decision.PASS,
                Map.of(),
                Instant.EPOCH
        );
        when(delegate.claim(
                "worker_01", Instant.EPOCH, Instant.EPOCH.plusSeconds(1)
        )).thenReturn(Optional.of(candidate));
        when(delegate.commit(candidate, "worker_01", score))
                .thenReturn(true);
        var repository = new TransactionalTrafficFraudRepository(delegate);

        assertThat(repository.claim(
                "worker_01", Instant.EPOCH, Instant.EPOCH.plusSeconds(1)
        )).contains(candidate);
        assertThat(repository.commit(candidate, "worker_01", score))
                .isTrue();
        repository.retry(
                candidate, "worker_01", Instant.EPOCH.plusSeconds(2)
        );

        verify(delegate).retry(
                candidate, "worker_01", Instant.EPOCH.plusSeconds(2)
        );
    }
}
