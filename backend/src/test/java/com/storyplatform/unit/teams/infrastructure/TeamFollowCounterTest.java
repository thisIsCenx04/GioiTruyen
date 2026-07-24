package com.storyplatform.unit.teams.infrastructure;

import com.storyplatform.shared.events.OutboxDelivery;
import com.storyplatform.teams.application.TeamFollowUseCase;
import com.storyplatform.teams.application.port.TeamFollowRepository;
import com.storyplatform.teams.infrastructure.TeamFollowCounterProjector;
import com.storyplatform.teams.infrastructure.TeamFollowCounterReconciler;
import com.storyplatform.teams.infrastructure.TeamFollowCounterStore;
import com.storyplatform.teams.infrastructure.persistence
        .MongoTeamFollowCounterDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TeamFollowCounterTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final TeamFollowCounterStore store =
            new TeamFollowCounterStore(
                    mongo,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void deltasNeverDriveProjectionBelowZeroAndCanReconcile() {
        store.applyDelta("team-1", 1);
        verify(mongo).upsert(
                any(Query.class),
                any(Update.class),
                eq(MongoTeamFollowCounterDocument.class)
        );

        store.applyDelta("team-1", -1);
        verify(mongo).updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoTeamFollowCounterDocument.class)
        );

        store.reconcile("team-1", 7);
        assertThatThrownBy(() -> store.applyDelta("team-1", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.reconcile("team-1", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void projectorValidatesAndAppliesVersionedOutboxPayload()
            throws Exception {
        TeamFollowCounterStore counters =
                mock(TeamFollowCounterStore.class);
        ObjectMapper mapper = new ObjectMapper();
        TeamFollowCounterProjector projector =
                new TeamFollowCounterProjector(counters, mapper);
        String payload = mapper.writeValueAsString(
                new TeamFollowUseCase.FollowChanged("team-1", 1)
        );

        assertThat(projector.consumer()).isEqualTo("team-follow-counter");
        assertThat(projector.eventType())
                .isEqualTo(TeamFollowUseCase.EVENT_TYPE);
        assertThat(projector.eventVersion()).isEqualTo(1);
        projector.handle(delivery("team-1", payload));
        verify(counters).applyDelta("team-1", 1);

        assertThatThrownBy(() ->
                projector.handle(delivery("team-2", payload)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                projector.handle(delivery("team-1", "{broken")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconcilerUsesAuthoritativeRelationCount() {
        TeamFollowRepository follows = mock(TeamFollowRepository.class);
        TeamFollowCounterStore counters =
                mock(TeamFollowCounterStore.class);
        org.mockito.Mockito.when(follows.count("team-1"))
                .thenReturn(9L);
        TeamFollowCounterReconciler reconciler =
                new TeamFollowCounterReconciler(follows, counters);

        assertThat(reconciler.reconcile("team-1")).isEqualTo(9);
        verify(counters).reconcile("team-1", 9);
    }

    private static OutboxDelivery delivery(
            String teamId,
            String payload
    ) {
        return new OutboxDelivery(
                "event-1",
                TeamFollowUseCase.EVENT_TYPE,
                1,
                NOW,
                "correlation-1",
                "team_follow",
                "team-1:user-1",
                "user-1",
                teamId,
                "application/json",
                payload
        );
    }
}
