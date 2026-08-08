package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.RewardOperations;
import com.storyplatform.monetization.infrastructure.RewardSettlementWorker;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RewardSettlementWorkerTest {

    @Test
    void settlesOnlyTheClosedPreviousUtcDay() {
        RewardOperations rewards = mock(RewardOperations.class);
        var worker = new RewardSettlementWorker(
                rewards,
                Clock.fixed(
                        Instant.parse("2026-07-25T00:15:00Z"),
                        ZoneOffset.UTC
                )
        );

        worker.settlePreviousUtcDay();

        verify(rewards).settle(LocalDate.of(2026, 7, 24));
    }
}
