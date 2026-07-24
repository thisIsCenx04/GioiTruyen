package com.storyplatform.unit.monetization.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.monetization.domain.RewardPeriod;
import com.storyplatform.monetization.domain.RewardSettlement;
import com.storyplatform.monetization.application.RewardException;
import com.storyplatform.monetization.domain.RewardAdjustment;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoRewardAdjustmentDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoRewardPeriodDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoRewardRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoRewardSettlementDocument;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoRewardRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");

    @Test
    void locksSnapshotAndRoundTripsVersionedHistory() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById(
                "2026-07-24",
                MongoRewardPeriodDocument.class
        )).thenReturn(periodDocument());
        when(mongo.find(
                any(Query.class),
                eq(MongoRewardSettlementDocument.class)
        )).thenReturn(List.of(settlementDocument()));
        var repository = new MongoRewardRepository(mongo);

        assertThat(repository.lock(
                period(),
                List.of(settlement())
        ).created()).isTrue();
        assertThat(repository.findPeriod("2026-07-24"))
                .contains(period());
        assertThat(repository.findByPeriod("2026-07-24"))
                .containsExactly(settlement());
        assertThat(repository.findRecentByTeam("team", 24))
                .containsExactly(settlement());
    }

    @Test
    void duplicatePeriodAbortsTheLosingTransactionForSafeRetry() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(MongoRewardPeriodDocument.class)))
                .thenThrow(new DuplicateKeyException("race"));
        assertThatThrownBy(() -> new MongoRewardRepository(mongo)
                .lock(period(), List.of()))
                .isInstanceOf(RewardException.class)
                .extracting("kind")
                .isEqualTo(RewardException.Kind.CONFLICT);
    }

    @Test
    void compareAndSetTransitionsReportWinnerAndLoser() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoRewardSettlementDocument.class)
        )).thenReturn(
                UpdateResult.acknowledged(1, 1L, null),
                UpdateResult.acknowledged(0, 0L, null)
        );
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoRewardPeriodDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoRewardRepository(mongo);

        assertThat(repository.markNoReward("id", NOW)).isTrue();
        assertThat(repository.markPosted("id", "ledger", NOW)).isFalse();
        assertThat(repository.markPeriodSettled("2026-07-24", NOW))
                .isTrue();
    }

    @Test
    void duplicateAdjustmentMapsToRetryableConflict() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(MongoRewardAdjustmentDocument.class)))
                .thenThrow(new DuplicateKeyException("race"));

        assertThatThrownBy(() -> new MongoRewardRepository(mongo)
                .insertAdjustment(new RewardAdjustment(
                        "40000000-0000-4000-8000-000000000001",
                        "10000000-0000-4000-8000-000000000001",
                        500, 100, 50, -50,
                        "FRAUD_CORRECTION", "a".repeat(64),
                        "30000000-0000-4000-8000-000000000001",
                        NOW
                ))).isInstanceOf(RewardException.class)
                .extracting("kind")
                .isEqualTo(RewardException.Kind.CONFLICT);
    }

    private static RewardPeriod period() {
        LocalDate date = LocalDate.of(2026, 7, 24);
        return new RewardPeriod(
                date.toString(), date,
                Instant.parse("2026-07-24T00:00:00Z"),
                Instant.parse("2026-07-25T00:00:00Z"),
                "reward-2026.1", 100, 100_000,
                "view-aggregate-2026.1", 1, 1_000,
                RewardPeriod.State.LOCKED,
                NOW.minusSeconds(60), null
        );
    }

    private static RewardSettlement settlement() {
        return new RewardSettlement(
                "10000000-0000-4000-8000-000000000001",
                "2026-07-24", "team",
                "20000000-0000-4000-8000-000000000001",
                1_000, 100, false, "reward-2026.1",
                "view-aggregate-2026.1", null,
                RewardSettlement.State.PENDING,
                NOW.minusSeconds(60), null
        );
    }

    private static MongoRewardPeriodDocument periodDocument() {
        LocalDate date = LocalDate.of(2026, 7, 24);
        return new MongoRewardPeriodDocument(
                date.toString(), date,
                Instant.parse("2026-07-24T00:00:00Z"),
                Instant.parse("2026-07-25T00:00:00Z"),
                "reward-2026.1", 100, 100_000,
                "view-aggregate-2026.1", 1, 1_000,
                "LOCKED", NOW.minusSeconds(60), null
        );
    }

    private static MongoRewardSettlementDocument settlementDocument() {
        return new MongoRewardSettlementDocument(
                "10000000-0000-4000-8000-000000000001",
                "2026-07-24", "team",
                "20000000-0000-4000-8000-000000000001",
                1_000, 100, false, "reward-2026.1",
                "view-aggregate-2026.1", null, "PENDING",
                NOW.minusSeconds(60), null
        );
    }
}
