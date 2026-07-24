package com.storyplatform.unit.publishing.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.publishing.domain.PublishingSchedule;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingDueRepository;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingScheduleDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoPublishingDueRepositoryTest {

    private static final String WORKER =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "30000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void atomicallyClaimsOldestDueScheduleAndCanDeferIt() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoPublishingScheduleDocument.class)
        )).thenReturn(MongoPublishingScheduleDocument.from(schedule()));
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoPublishingScheduleDocument.class)
        )).thenReturn(updated(1, 1));
        var repository = new MongoPublishingDueRepository(mongo);

        assertThat(repository.claim(
                WORKER, NOW, NOW.plusSeconds(30)
        )).contains(schedule());
        assertThat(repository.defer(
                schedule(), WORKER, NOW.plusSeconds(300), NOW
        )).isTrue();
    }

    @Test
    void noClaimAndStaleLeaseAreNoops() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoPublishingScheduleDocument.class)
        )).thenReturn(updated(0, 0));
        var repository = new MongoPublishingDueRepository(mongo);

        assertThat(repository.claim(
                WORKER, NOW, NOW.plusSeconds(30)
        )).isEmpty();
        assertThat(repository.defer(
                schedule(), WORKER, NOW.plusSeconds(300), NOW
        )).isFalse();
    }

    @Test
    void publishesOnlyExactScheduleStoryAndFrozenChapters() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoPublishingScheduleDocument.class)
        )).thenReturn(updated(1, 1));
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq("stories")
        )).thenReturn(updated(1, 1));
        when(mongo.updateMulti(
                any(Query.class),
                any(Update.class),
                eq("chapters")
        )).thenReturn(updated(2, 2));
        var repository = new MongoPublishingDueRepository(mongo);

        assertThat(repository.publish(
                schedule(), WORKER, NOW
        )).isTrue();

        when(mongo.updateMulti(
                any(Query.class),
                any(Update.class),
                eq("chapters")
        )).thenReturn(updated(1, 1));
        assertThat(repository.publish(
                schedule(), WORKER, NOW
        )).isFalse();
    }

    @Test
    void staleScheduleOrStoryStopsDownstreamWrites() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoPublishingScheduleDocument.class)
        )).thenReturn(updated(0, 0));
        var repository = new MongoPublishingDueRepository(mongo);
        assertThat(repository.publish(
                schedule(), WORKER, NOW
        )).isFalse();
        verify(mongo, never()).updateMulti(
                any(Query.class),
                any(Update.class),
                eq("chapters")
        );

        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoPublishingScheduleDocument.class)
        )).thenReturn(updated(1, 1));
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq("stories")
        )).thenReturn(updated(0, 0));
        assertThat(repository.publish(
                schedule(), WORKER, NOW
        )).isFalse();
    }

    private static UpdateResult updated(long matched, long modified) {
        return UpdateResult.acknowledged(matched, modified, null);
    }

    private static PublishingSchedule schedule() {
        return new PublishingSchedule(
                "70000000-0000-4000-8000-000000000001",
                PublishingSchedule.TargetType.STORY,
                STORY,
                TEAM,
                "40000000-0000-4000-8000-000000000001",
                List.of(chapter(1), chapter(2)),
                PublishingSchedule.State.SCHEDULED,
                NOW.minusSeconds(1),
                "UTC",
                "80000000-0000-4000-8000-000000000001",
                NOW.minusSeconds(3600),
                NOW,
                2
        );
    }

    private static PublishingSchedule.FrozenChapterRevision chapter(
            int number
    ) {
        return new PublishingSchedule.FrozenChapterRevision(
                "50000000-0000-4000-8000-00000000000" + number,
                "60000000-0000-4000-8000-00000000000" + number,
                number
        );
    }
}
