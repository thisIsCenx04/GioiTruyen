package com.storyplatform.unit.analytics.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.analytics.application.port.ViewAggregateRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoReadingViewValidationRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoViewAggregateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoViewAggregateRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private static final String STORY =
            "20000000-0000-4000-8000-000000000001";

    @Test
    void claimsScoredClassificationAndNormalizesReasons() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoViewAggregateRepository
                        .AggregateCandidateDocument.class),
                eq(MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION)
        )).thenReturn(
                document(null),
                (MongoViewAggregateRepository.AggregateCandidateDocument) null
        );
        var repository = new MongoViewAggregateRepository(mongo);

        var claimed = repository.claim(
                "worker_01", NOW, NOW.plusSeconds(30)
        ).orElseThrow();

        assertThat(claimed.reasons()).isEmpty();
        assertThat(repository.claim(
                "worker_01", NOW, NOW.plusSeconds(30)
        )).isEmpty();
    }

    @Test
    void atomicallyIncrementsHourAndDayThenMarksSource() {
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
        var repository = new MongoViewAggregateRepository(mongo);
        var candidate = candidate();
        var delta = new ViewAggregateRepository.Delta(
                1, 1, 0, 1, Set.of("DUPLICATE")
        );

        assertThat(repository.commit(
                candidate, "worker_01", delta, NOW
        )).isTrue();
        assertThat(repository.commit(
                candidate, "worker_01", delta, NOW
        )).isFalse();
        repository.retry(
                candidate, "worker_01", NOW.plusSeconds(30)
        );

        verify(mongo, times(4)).upsert(
                any(Query.class),
                any(Update.class),
                eq(MongoViewAggregateRepository.COLLECTION)
        );
    }

    @Test
    void rebuildResetRemovesProjectionAndReleasesSourceMarkers() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository = new MongoViewAggregateRepository(mongo);
        Instant to = NOW.plusSeconds(3600);

        repository.reset(STORY, NOW, to);

        verify(mongo).remove(
                any(Query.class),
                eq(MongoViewAggregateRepository.COLLECTION)
        );
        verify(mongo).updateMulti(
                any(Query.class),
                any(Update.class),
                eq(MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION)
        );
    }

    @Test
    void reconcilesRawClassificationCountsToHourlyProjection() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.count(
                any(Query.class),
                eq(MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION)
        )).thenReturn(3L, 2L, 1L);
        when(mongo.find(
                any(Query.class),
                eq(MongoViewAggregateRepository.AggregateDocument.class),
                eq(MongoViewAggregateRepository.COLLECTION)
        )).thenReturn(List.of(
                new MongoViewAggregateRepository.AggregateDocument(
                        "hour",
                        STORY,
                        "HOUR",
                        NOW,
                        3,
                        2,
                        1,
                        1
                )
        ));
        var repository = new MongoViewAggregateRepository(mongo);

        var result = repository.reconcile(
                STORY, NOW, NOW.plusSeconds(3600)
        );

        assertThat(result.matches()).isTrue();
        assertThat(result.expectedRawEvents()).isEqualTo(3);
        assertThat(result.actualValidViews()).isEqualTo(1);

        var mismatch = new com.storyplatform.analytics.application
                .ViewAggregateOperations.Reconciliation(
                3, 2, 2, 2, 1, 1, 1, 1
        );
        assertThat(mismatch.matches()).isFalse();
    }

    private static MongoViewAggregateRepository.AggregateCandidateDocument
    document(Set<String> reasons) {
        return new MongoViewAggregateRepository.AggregateCandidateDocument(
                "classification",
                STORY,
                "COMPLETION",
                NOW,
                false,
                reasons,
                "REVIEW"
        );
    }

    private static ViewAggregateRepository.Candidate candidate() {
        return new ViewAggregateRepository.Candidate(
                "classification",
                STORY,
                "COMPLETION",
                NOW,
                false,
                Set.of("DUPLICATE"),
                "REVIEW"
        );
    }
}
