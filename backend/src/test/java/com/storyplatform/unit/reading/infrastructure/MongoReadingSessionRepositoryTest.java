package com.storyplatform.unit.reading.infrastructure;

import com.storyplatform.reading.application.port.ReadingSessionRepository;
import com.storyplatform.reading.application.port.ReadingHeartbeatRepository;
import com.storyplatform.reading.application.port.ReadingCompletionRepository;
import com.storyplatform.reading.infrastructure.persistence
        .MongoReadingSessionRepository;
import com.storyplatform.reading.infrastructure.persistence
        .MongoReadingSessionRepository.SessionDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoReadingSessionRepositoryTest {

    @Test
    void requiresPublishedChapterAndStoryAndStoresOnlyOpaqueActor() {
        var mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(Query.class), eq("chapters")))
                .thenReturn(true);
        when(mongo.exists(any(Query.class), eq("stories")))
                .thenReturn(true);
        var repository = new MongoReadingSessionRepository(mongo);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        var session = new ReadingSessionRepository.SessionRecord(
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                "ANONYMOUS",
                "opaque-ref",
                now,
                now.plusSeconds(1800),
                now.plusSeconds(1800 + 604800)
        );

        assertThat(repository.chapterIsPublished(
                session.storyId(),
                session.chapterId()
        )).isTrue();
        repository.create(session);

        verify(mongo).insert(
                new SessionDocument(
                        session.id(),
                        session.storyId(),
                        session.chapterId(),
                        "ANONYMOUS",
                        "opaque-ref",
                        session.startedAt(),
                        session.expiresAt(),
                        session.purgeAt(),
                        "ACTIVE",
                        0,
                        java.util.List.of(),
                        now
                ),
                MongoReadingSessionRepository.COLLECTION
        );
    }

    @Test
    void rejectsAChapterWhenItsStoryIsNotPublished() {
        var mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(Query.class), eq("chapters")))
                .thenReturn(true);
        when(mongo.exists(any(Query.class), eq("stories")))
                .thenReturn(false);

        assertThat(new MongoReadingSessionRepository(mongo)
                .chapterIsPublished("story", "chapter")).isFalse();
    }

    @Test
    void advancesOnceAndClassifiesReplayConflictAndInactiveState() {
        var mongo = mock(MongoTemplate.class);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        String sessionId = "40000000-0000-4000-8000-000000000001";
        String batchId = "50000000-0000-4000-8000-000000000001";
        SessionDocument active = document(
                sessionId,
                now,
                java.util.List.of(batchId)
        );
        when(mongo.findAndModify(
                any(),
                any(),
                any(),
                eq(SessionDocument.class),
                eq(MongoReadingSessionRepository.COLLECTION)
        )).thenReturn(active, null, null, null);
        when(mongo.findById(
                sessionId,
                SessionDocument.class,
                MongoReadingSessionRepository.COLLECTION
        )).thenReturn(
                active,
                document(sessionId, now, java.util.List.of()),
                null
        );
        var repository = new MongoReadingSessionRepository(mongo);

        assertThat(repository.apply(
                sessionId, "actor", batchId, 0, 1, now
        )).isEqualTo(ReadingHeartbeatRepository.ApplyResult.APPLIED);
        assertThat(repository.apply(
                sessionId, "actor", batchId, 0, 1, now
        )).isEqualTo(ReadingHeartbeatRepository.ApplyResult.DUPLICATE);
        assertThat(repository.apply(
                sessionId, "actor", batchId, 0, 1, now
        )).isEqualTo(
                ReadingHeartbeatRepository.ApplyResult.SEQUENCE_CONFLICT
        );
        assertThat(repository.apply(
                sessionId, "actor", batchId, 0, 1, now
        )).isEqualTo(ReadingHeartbeatRepository.ApplyResult.NOT_ACTIVE);
    }

    private static SessionDocument document(
            String sessionId,
            Instant now,
            java.util.List<String> batches
    ) {
        return new SessionDocument(
                sessionId,
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                "ANONYMOUS",
                "actor",
                now.minusSeconds(60),
                now.plusSeconds(60),
                now.plusSeconds(604800),
                "ACTIVE",
                1,
                batches,
                now
        );
    }

    @Test
    void normalizesLegacyNullBatchReceipts() {
        assertThat(new SessionDocument(
                "id",
                "story",
                "chapter",
                "ANONYMOUS",
                "actor",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(60),
                Instant.EPOCH.plusSeconds(120),
                "ACTIVE",
                0,
                null,
                Instant.EPOCH
        ).processedBatchIds()).isEmpty();
    }

    @Test
    void completionIsAtomicRetrySafeAndTimeoutAware() {
        var mongo = mock(MongoTemplate.class);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        String sessionId = "40000000-0000-4000-8000-000000000001";
        SessionDocument active = document(
                sessionId,
                now,
                java.util.List.of()
        );
        when(mongo.findAndModify(
                any(),
                any(),
                any(),
                eq(SessionDocument.class),
                eq(MongoReadingSessionRepository.COLLECTION)
        )).thenReturn(active, null, null, null);
        when(mongo.exists(
                any(Query.class),
                eq(MongoReadingSessionRepository.COLLECTION)
        )).thenReturn(true, false, true, false, false);
        var repository = new MongoReadingSessionRepository(mongo);

        assertThat(repository.complete(
                sessionId, "actor", "completion", 1, now, now
        )).isEqualTo(ReadingCompletionRepository.CompleteResult.APPLIED);
        assertThat(repository.complete(
                sessionId, "actor", "completion", 1, now, now
        )).isEqualTo(ReadingCompletionRepository.CompleteResult.DUPLICATE);
        assertThat(repository.complete(
                sessionId, "actor", "completion", 2, now, now
        )).isEqualTo(
                ReadingCompletionRepository.CompleteResult.SEQUENCE_CONFLICT
        );
        assertThat(repository.complete(
                sessionId, "actor", "completion", 1, now, now
        )).isEqualTo(
                ReadingCompletionRepository.CompleteResult.NOT_ACTIVE
        );
    }
}
