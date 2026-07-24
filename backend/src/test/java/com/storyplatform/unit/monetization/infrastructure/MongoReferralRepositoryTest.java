package com.storyplatform.unit.monetization.infrastructure;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import com.storyplatform.monetization.application.ReferralException;
import com.storyplatform.monetization.domain.ReferralAttribution;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoReferralAttributionDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoReferralRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoReferralRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");

    @Test
    void roundTripsLookupQuotaAndRewardableQuery() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var document = document();
        when(mongo.findOne(
                any(Query.class),
                eq(MongoReferralAttributionDocument.class)
        )).thenReturn(document);
        when(mongo.find(
                any(Query.class),
                eq(MongoReferralAttributionDocument.class)
        )).thenReturn(List.of(document));
        when(mongo.count(
                any(Query.class),
                eq(MongoReferralAttributionDocument.class)
        )).thenReturn(3L);
        var repository = new MongoReferralRepository(mongo);

        assertThat(repository.findByRefereeId("referee"))
                .contains(attribution());
        assertThat(repository.findByIdempotencyKeyHash("hash"))
                .get().extracting("requestHash").isEqualTo("b".repeat(64));
        assertThat(repository.countRecentByReferrer(
                "referrer", NOW.minusSeconds(60)
        )).isEqualTo(3);
        assertThat(repository.findRewardable(NOW, 100))
                .containsExactly(attribution());
    }

    @Test
    void duplicateAttributionMapsToConflictAndCasReportsWinner() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(MongoReferralAttributionDocument.class)))
                .thenThrow(new DuplicateKeyException("race"));
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoReferralAttributionDocument.class)
        )).thenReturn(
                UpdateResult.acknowledged(1, 1L, null),
                UpdateResult.acknowledged(0, 0L, null)
        );
        var repository = new MongoReferralRepository(mongo);

        assertThatThrownBy(() -> repository.insert(
                attribution(),
                "c".repeat(64),
                "b".repeat(64)
        )).isInstanceOf(ReferralException.class)
                .extracting("kind")
                .isEqualTo(ReferralException.Kind.CONFLICT);
        assertThat(repository.markRewarded(
                document().id(),
                "40000000-0000-4000-8000-000000000001",
                NOW
        )).isTrue();
        assertThat(repository.markRewarded(
                document().id(),
                "40000000-0000-4000-8000-000000000001",
                NOW
        )).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void computesBoundedServerSideSummaryAndHandlesEmptyResult() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        AggregateIterable<Document> aggregate =
                mock(AggregateIterable.class);
        when(mongo.getCollection(
                MongoReferralAttributionDocument.COLLECTION
        )).thenReturn(collection);
        when(collection.aggregate(any())).thenReturn(aggregate);
        doAnswer(invocation -> {
            java.util.function.Consumer<Document> consumer =
                    invocation.getArgument(0);
            consumer.accept(new Document("referredCount", 4L)
                    .append("pendingCount", 1L)
                    .append("rewardedCount", 3L)
                    .append("rewardedXu", 300L));
            return null;
        }).doNothing().when(aggregate).forEach(any());
        var repository = new MongoReferralRepository(mongo);

        assertThat(repository.summary("referrer"))
                .isEqualTo(new com.storyplatform.monetization.application
                        .port.ReferralRepository.Summary(4, 1, 3, 300));
        assertThat(repository.summary("nobody"))
                .isEqualTo(new com.storyplatform.monetization.application
                        .port.ReferralRepository.Summary(0, 0, 0, 0));
    }

    private static MongoReferralAttributionDocument document() {
        return new MongoReferralAttributionDocument(
                "10000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                "a".repeat(64),
                "referral-2026.1",
                100,
                ReferralAttribution.State.PENDING.name(),
                List.of(),
                null,
                NOW.minusSeconds(60),
                NOW,
                null,
                "c".repeat(64),
                "b".repeat(64)
        );
    }

    private static ReferralAttribution attribution() {
        return new ReferralAttribution(
                "10000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                "a".repeat(64),
                "referral-2026.1",
                100,
                ReferralAttribution.State.PENDING,
                List.of(),
                null,
                NOW.minusSeconds(60),
                NOW,
                null
        );
    }
}
