package com.storyplatform.unit.monetization.domain;

import com.storyplatform.monetization.domain.RewardAdjustment;
import com.storyplatform.monetization.domain.RewardPeriod;
import com.storyplatform.monetization.domain.RewardSettlement;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RewardDomainTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 7, 24);

    @Test
    void periodTransitionsOnlyOnceAfterLock() {
        RewardPeriod settled = period().settle(NOW);

        assertThat(settled.state()).isEqualTo(RewardPeriod.State.SETTLED);
        assertThatThrownBy(() -> settled.settle(NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> period().settle(
                NOW.minusSeconds(120)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void periodRejectsInvalidIdentityRangeRuleAndState() {
        assertInvalidPeriod("wrong", "reward-2026.1", 100, 1, 1, null);
        assertInvalidPeriod(
                DATE.toString(), "BAD VERSION", 100, 1, 1, null
        );
        assertInvalidPeriod(
                DATE.toString(), "reward-2026.1", 0, 1, 1, null
        );
        assertInvalidPeriod(
                DATE.toString(), "reward-2026.1", 100, -1, 1, null
        );
        assertInvalidPeriod(
                DATE.toString(), "reward-2026.1", 100, 1, -1, null
        );
        assertInvalidPeriod(
                DATE.toString(), "reward-2026.1", 100, 1, 1, NOW
        );
    }

    @Test
    void settlementSupportsPostedAndZeroRewardTerminalStates() {
        RewardSettlement pending = pending(100);
        assertThat(pending.posted(
                "30000000-0000-4000-8000-000000000001",
                NOW
        ).state()).isEqualTo(RewardSettlement.State.POSTED);
        assertThat(pending(0).noReward(NOW).state())
                .isEqualTo(RewardSettlement.State.NO_REWARD);
        assertThatThrownBy(() -> pending(100).noReward(NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending(0).posted(
                "30000000-0000-4000-8000-000000000001",
                NOW
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void settlementRejectsInconsistentPersistenceStates() {
        assertInvalidSettlement("", 1, null,
                RewardSettlement.State.PENDING, null);
        assertInvalidSettlement("team", -1, null,
                RewardSettlement.State.PENDING, null);
        assertInvalidSettlement("team", 1,
                "30000000-0000-4000-8000-000000000001",
                RewardSettlement.State.PENDING, null);
        assertInvalidSettlement("team", 1, null,
                RewardSettlement.State.POSTED, NOW);
        assertInvalidSettlement("team", 1, null,
                RewardSettlement.State.NO_REWARD, NOW);
    }

    @Test
    void adjustmentRequiresAConsistentNonZeroCompensation() {
        assertThat(adjustment(-50).deltaXu()).isEqualTo(-50);
        assertThatThrownBy(() -> adjustment(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RewardAdjustment(
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                500, 100, 50, -40, "FRAUD_CORRECTION",
                "a".repeat(64),
                "30000000-0000-4000-8000-000000000001", NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RewardAdjustment(
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                500, 100, 50, -50, "bad",
                "invalid",
                "30000000-0000-4000-8000-000000000001", NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static RewardPeriod period() {
        return new RewardPeriod(
                DATE.toString(), DATE,
                DATE.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                DATE.plusDays(1).atStartOfDay(
                        java.time.ZoneOffset.UTC
                ).toInstant(),
                "reward-2026.1", 100, 1_000,
                "view-aggregate-2026.1", 1, 1_000,
                RewardPeriod.State.LOCKED,
                NOW.minusSeconds(60), null
        );
    }

    private static void assertInvalidPeriod(
            String id,
            String version,
            long rate,
            long teams,
            long views,
            Instant settledAt
    ) {
        assertThatThrownBy(() -> new RewardPeriod(
                id, DATE,
                DATE.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                DATE.plusDays(1).atStartOfDay(
                        java.time.ZoneOffset.UTC
                ).toInstant(),
                version, rate, 1_000, "view-aggregate-2026.1",
                teams, views, RewardPeriod.State.LOCKED,
                NOW.minusSeconds(60), settledAt
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static RewardSettlement pending(long amount) {
        return new RewardSettlement(
                "10000000-0000-4000-8000-000000000001",
                DATE.toString(), "team",
                "20000000-0000-4000-8000-000000000001",
                1_000, amount, false, "reward-2026.1",
                "view-aggregate-2026.1", null,
                RewardSettlement.State.PENDING,
                NOW.minusSeconds(60), null
        );
    }

    private static void assertInvalidSettlement(
            String team,
            long amount,
            String ledger,
            RewardSettlement.State state,
            Instant postedAt
    ) {
        assertThatThrownBy(() -> new RewardSettlement(
                "10000000-0000-4000-8000-000000000001",
                DATE.toString(), team,
                "20000000-0000-4000-8000-000000000001",
                1_000, amount, false, "reward-2026.1",
                "view-aggregate-2026.1", ledger, state,
                NOW.minusSeconds(60), postedAt
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static RewardAdjustment adjustment(long delta) {
        return new RewardAdjustment(
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                500, 100, 100 + delta, delta,
                "FRAUD_CORRECTION", "a".repeat(64),
                "30000000-0000-4000-8000-000000000001", NOW
        );
    }
}
