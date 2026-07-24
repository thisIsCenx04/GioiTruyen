package com.storyplatform.unit.publishing.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.publishing.application.port
        .ContentVisibilityRepository;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoContentVisibilityRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoContentVisibilityRepositoryTest {

    private static final String STORY =
            "30000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void loadsVisibilityAndChangesStoryChaptersAndAudit() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findOne(
                any(Query.class), eq(Document.class), eq("stories")
        )).thenReturn(new Document()
                .append("teamId", TEAM)
                .append("workflowStatus", "PUBLISHED")
                .append("version", 4L));
        when(mongo.updateFirst(
                any(Query.class), any(Update.class), eq("stories")
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoContentVisibilityRepository(
                mongo,
                () -> "90000000-0000-4000-8000-000000000001"
        );

        var candidate = repository.find(STORY).orElseThrow();
        assertThat(candidate.state()).isEqualTo("PUBLISHED");
        assertThat(repository.change(
                candidate,
                "HIDDEN",
                "10000000-0000-4000-8000-000000000001",
                "HIDE",
                "OWNER_REQUEST",
                "",
                NOW
        )).isTrue();
        verify(mongo).updateMulti(
                any(Query.class), any(Update.class), eq("chapters")
        );
        verify(mongo).insert(any(Document.class), eq("audit_logs"));
    }

    @Test
    void missingOrRacedStoryHasNoSideEffects() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository = new MongoContentVisibilityRepository(
                mongo,
                () -> "90000000-0000-4000-8000-000000000001"
        );
        assertThat(repository.find(STORY)).isEmpty();
        when(mongo.updateFirst(
                any(Query.class), any(Update.class), eq("stories")
        )).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        assertThat(repository.change(
                new ContentVisibilityRepository.Candidate(
                        STORY, TEAM, "PUBLISHED", null, 4
                ),
                "SUSPENDED",
                "10000000-0000-4000-8000-000000000001",
                "SUSPEND",
                "POLICY",
                "note",
                NOW
        )).isFalse();
        verify(mongo, never()).insert(
                any(Document.class), eq("audit_logs")
        );
    }
}
