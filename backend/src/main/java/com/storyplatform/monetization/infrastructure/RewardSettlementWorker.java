package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.RewardOperations;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

public final class RewardSettlementWorker {

    private final RewardOperations rewards;
    private final Clock clock;

    public RewardSettlementWorker(
            RewardOperations rewards,
            Clock clock
    ) {
        this.rewards = Objects.requireNonNull(rewards);
        this.clock = Objects.requireNonNull(clock);
    }

    @Scheduled(
            cron = "${app.monetization.rewards.cron:0 */5 * * * *}",
            zone = "UTC"
    )
    public void settlePreviousUtcDay() {
        rewards.settle(
                LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1)
        );
    }
}
