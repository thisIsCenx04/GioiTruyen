package com.storyplatform.unit.analytics.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.analytics.application.TrafficFraudScorer;
import com.storyplatform.analytics.application.port.TrafficFraudRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoReadingViewValidationRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoTrafficFraudRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoTrafficFraudRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void claimsClassificationsAndNormalizesLegacyNullReasons() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoTrafficFraudRepository
                        .FraudCandidateDocument.class),
                eq(MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION)
        )).thenReturn(
                document(null),
                document(Set.of("SELF_VIEW")),
                null
        );
        var repository = new MongoTrafficFraudRepository(mongo);

        assertThat(repository.claim(
                "worker_01", NOW, NOW.plusSeconds(30)
        ).orElseThrow().signals()).isEmpty();
        assertThat(repository.claim(
                "worker_01", NOW, NOW.plusSeconds(30)
        ).orElseThrow().signals()).containsExactly("SELF_VIEW");
        assertThat(repository.claim(
                "worker_01", NOW, NOW.plusSeconds(30)
        )).isEmpty();
    }

    @Test
    void completesByLeaseAndCreatesOnlyReviewOrHoldCaseStates() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        UpdateResult changed = mock(UpdateResult.class);
        UpdateResult unchanged = mock(UpdateResult.class);
        when(changed.getModifiedCount()).thenReturn(1L);
        when(unchanged.getModifiedCount()).thenReturn(0L);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION)
        )).thenReturn(changed, unchanged, changed);
        var repository = new MongoTrafficFraudRepository(mongo);
        var candidate = candidate();

        assertThat(repository.commit(
                candidate, "worker_01", score(
                        TrafficFraudScorer.Decision.REVIEW, 50
                )
        )).isTrue();
        assertThat(repository.commit(
                candidate, "worker_01", score(
                        TrafficFraudScorer.Decision.REVIEW, 50
                )
        )).isFalse();
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION)
        )).thenReturn(changed);
        repository.commit(
                candidate, "worker_01",
                score(TrafficFraudScorer.Decision.HOLD_FOR_REVIEW, 80)
        );
        repository.retry(
                candidate,
                "worker_01",
                NOW.plusSeconds(30)
        );

        verify(mongo, times(2)).upsert(
                any(Query.class),
                any(Update.class),
                eq(MongoTrafficFraudRepository.CASE_COLLECTION)
        );
    }

    private static MongoTrafficFraudRepository.FraudCandidateDocument
    document(Set<String> reasons) {
        return new MongoTrafficFraudRepository.FraudCandidateDocument(
                "classification",
                "event",
                "actor",
                "story",
                reasons,
                NOW
        );
    }

    private static TrafficFraudRepository.Candidate candidate() {
        return new TrafficFraudRepository.Candidate(
                "classification",
                "event",
                "actor",
                "story",
                Set.of("SELF_VIEW", "DUPLICATE")
        );
    }

    private static TrafficFraudScorer.Score score(
            TrafficFraudScorer.Decision decision,
            int value
    ) {
        return new TrafficFraudScorer.Score(
                TrafficFraudScorer.RULE_VERSION,
                value,
                decision,
                Map.of("SELF_VIEW", 30, "DUPLICATE", 20),
                NOW
        );
    }
}
