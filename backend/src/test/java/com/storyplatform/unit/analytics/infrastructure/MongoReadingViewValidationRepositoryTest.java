package com.storyplatform.unit.analytics.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.analytics.application.RawReadingEvent;
import com.storyplatform.analytics.application.ReadingViewClassifier;
import com.storyplatform.analytics.application.port
        .ReadingViewValidationRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoRawReadingEventRepository;
import com.storyplatform.analytics.infrastructure.persistence
        .MongoReadingViewValidationRepository;
import com.storyplatform.reading.application.contract
        .ReadingActorReferences;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoReadingViewValidationRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final ReadingActorReferences actors =
            mock(ReadingActorReferences.class);

    @Test
    void claimsOnlyUnvalidatedSnapshotAndMapsRawFields() {
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoReadingViewValidationRepository
                        .BucketDocument.class),
                eq(MongoRawReadingEventRepository.COLLECTION)
        )).thenReturn(new MongoReadingViewValidationRepository.BucketDocument(
                "bucket",
                NOW,
                2,
                1,
                List.of(embedded("old", 1), embedded("new", 2))
        ), null);
        var repository = repository();

        var claimed = repository.claim(
                "worker_01",
                NOW,
                NOW,
                NOW.plusSeconds(30)
        ).orElseThrow();

        assertThat(claimed.validatedCount()).isEqualTo(1);
        assertThat(claimed.snapshotCount()).isEqualTo(2);
        assertThat(claimed.events()).extracting(RawReadingEvent::eventId)
                .containsExactly("new");
        assertThat(repository.claim(
                "worker_01",
                NOW,
                NOW,
                NOW.plusSeconds(30)
        )).isEmpty();
    }

    @Test
    void rejectsCorruptBucketCounts() {
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoReadingViewValidationRepository
                        .BucketDocument.class),
                eq(MongoRawReadingEventRepository.COLLECTION)
        )).thenReturn(new MongoReadingViewValidationRepository.BucketDocument(
                "bucket", NOW, 1, 1, List.of(embedded("event", 1))
        ));

        assertThatThrownBy(() -> repository().claim(
                "worker_01", NOW, NOW, NOW.plusSeconds(30)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deduplicatesLogicalFingerprintButAllowsReplayOfFirstEvent() {
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoReadingViewValidationRepository
                        .FingerprintDocument.class),
                eq(MongoReadingViewValidationRepository
                        .FINGERPRINT_COLLECTION)
        )).thenReturn(
                null,
                new MongoReadingViewValidationRepository
                        .FingerprintDocument(
                        "fingerprint", "first", NOW, NOW.plusSeconds(60)
                ),
                new MongoReadingViewValidationRepository
                        .FingerprintDocument(
                        "fingerprint", "first", NOW, NOW.plusSeconds(60)
                )
        );
        var repository = repository();

        assertThat(repository.claimFingerprint(
                "fingerprint", "first", NOW
        )).isFalse();
        assertThat(repository.claimFingerprint(
                "fingerprint", "first", NOW
        )).isFalse();
        assertThat(repository.claimFingerprint(
                "fingerprint", "second", NOW
        )).isTrue();
    }

    @Test
    void detectsSelfViewOnlyForAnActiveTeamMember() {
        when(mongo.findById(
                eq("story"),
                eq(MongoReadingViewValidationRepository
                        .StoryTeamProjection.class),
                eq("stories")
        )).thenReturn(
                null,
                new MongoReadingViewValidationRepository
                        .StoryTeamProjection("story", null),
                new MongoReadingViewValidationRepository
                        .StoryTeamProjection("story", "team"),
                new MongoReadingViewValidationRepository
                        .StoryTeamProjection("story", "team")
        );
        when(mongo.find(
                any(Query.class),
                eq(MongoReadingViewValidationRepository
                        .MemberProjection.class),
                eq("team_memberships")
        )).thenReturn(List.of(
                new MongoReadingViewValidationRepository.MemberProjection(
                        "membership", null
                ),
                new MongoReadingViewValidationRepository.MemberProjection(
                        "membership-2", "user"
                )
        ));
        when(actors.authenticatedUser("user")).thenReturn("member-ref");
        var repository = repository();

        assertThat(repository.isSelfView(event("other-ref"))).isFalse();
        assertThat(repository.isSelfView(event("other-ref"))).isFalse();
        assertThat(repository.isSelfView(event("other-ref"))).isFalse();
        assertThat(repository.isSelfView(event("member-ref"))).isTrue();
    }

    @Test
    void explainsAutomationSignalsFromAdjacentEvents() {
        var repository = repository();
        RawReadingEvent previous = event(
                "actor",
                NOW.plusSeconds(10),
                10,
                1
        );

        assertThat(repository.botSignals(event("actor"), null)).isEmpty();
        assertThat(repository.botSignals(
                event("actor", NOW.plusSeconds(10), 50, 10),
                previous
        )).containsExactlyInAnyOrder(
                "EVENT_TIME_REGRESSION",
                "IMPOSSIBLE_PROGRESS",
                "ACTIVE_TIME_EXCEEDS_CADENCE"
        );
        assertThat(repository.botSignals(
                event("actor", NOW.plusSeconds(30), 20, 10),
                previous
        )).isEmpty();
    }

    @Test
    void savesVersionedResultsAndCompletesOrRetriesByLease() {
        BulkOperations bulk = mock(BulkOperations.class);
        when(mongo.bulkOps(
                BulkOperations.BulkMode.UNORDERED,
                MongoReadingViewValidationRepository
                        .ClassificationDocument.class,
                MongoReadingViewValidationRepository
                        .CLASSIFICATION_COLLECTION
        )).thenReturn(bulk);
        when(bulk.upsert(any(Query.class), any(Update.class)))
                .thenReturn(bulk);
        UpdateResult changed = mock(UpdateResult.class);
        UpdateResult unchanged = mock(UpdateResult.class);
        when(changed.getModifiedCount()).thenReturn(1L);
        when(unchanged.getModifiedCount()).thenReturn(0L);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoRawReadingEventRepository.COLLECTION)
        )).thenReturn(changed, unchanged, changed);
        var repository = repository();
        var bucket = new ReadingViewValidationRepository.ClaimedBucket(
                "bucket", 0, 1, List.of(event("actor"))
        );
        repository.save(List.of());
        repository.save(List.of(new ReadingViewClassifier.Classification(
                "event",
                "fingerprint",
                "session",
                "actor",
                "story",
                "chapter",
                RawReadingEvent.Kind.HEARTBEAT,
                ReadingViewClassifier.RULE_VERSION,
                false,
                Set.of("DUPLICATE"),
                NOW
        )));

        verify(bulk).execute();
        assertThat(repository.complete(bucket, "worker_01", NOW)).isTrue();
        assertThat(repository.complete(bucket, "worker_01", NOW)).isFalse();
        repository.retry(
                bucket,
                "worker_01",
                NOW.plusSeconds(30),
                "VALIDATION_FAILED"
        );
    }

    @Test
    void validatesFingerprintRetention() {
        for (Duration value : new Duration[]{
                null, Duration.ofDays(6), Duration.ofDays(366)
        }) {
            assertThatThrownBy(() ->
                    new MongoReadingViewValidationRepository(
                            mongo, actors, value
                    )).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new MongoReadingViewValidationRepository(
                mongo, actors, Duration.ofDays(365)
        )).isNotNull();
    }

    private MongoReadingViewValidationRepository repository() {
        return new MongoReadingViewValidationRepository(
                mongo,
                actors,
                Duration.ofDays(90)
        );
    }

    private static MongoRawReadingEventRepository.EmbeddedEvent embedded(
            String id,
            long sequence
    ) {
        return new MongoRawReadingEventRepository.EmbeddedEvent(
                id,
                "HEARTBEAT",
                "a".repeat(64),
                "actor",
                "story",
                "chapter",
                sequence,
                NOW,
                50,
                10,
                NOW
        );
    }

    private static RawReadingEvent event(String actorRef) {
        return event(actorRef, NOW, 50, 10);
    }

    private static RawReadingEvent event(
            String actorRef,
            Instant occurredAt,
            double position,
            int active
    ) {
        return new RawReadingEvent(
                "event",
                RawReadingEvent.Kind.HEARTBEAT,
                "a".repeat(64),
                actorRef,
                "story",
                "chapter",
                1,
                occurredAt,
                position,
                active,
                NOW
        );
    }
}
