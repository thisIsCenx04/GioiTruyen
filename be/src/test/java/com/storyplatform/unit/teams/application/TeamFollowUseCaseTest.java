package com.storyplatform.unit.teams.application;

import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.TeamFollowUseCase;
import com.storyplatform.teams.application.TeamNotFoundException;
import com.storyplatform.teams.application.port.TeamFollowRepository;
import com.storyplatform.teams.application.port.TeamRepository;
import com.storyplatform.teams.domain.Team;
import com.storyplatform.teams.domain.TeamFollow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamFollowUseCaseTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );
    private final TeamRepository teams = mock(TeamRepository.class);
    private final InMemoryFollows follows = new InMemoryFollows();
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private TeamFollowUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TeamFollowUseCase(
                teams,
                follows,
                outbox,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(teams.findById("team-1")).thenReturn(Optional.of(team(
                Team.State.ACTIVE
        )));
    }

    @Test
    void followAndUnfollowAreIdempotentAndEmitOnlyRealDeltas() {
        assertThat(useCase.follow("user-1", "team-1").following())
                .isTrue();
        assertThat(useCase.follow("user-1", "team-1").followerCount())
                .isEqualTo(1);
        assertThat(useCase.status("user-1", "team-1").following())
                .isTrue();
        assertThat(useCase.unfollow("user-1", "team-1").following())
                .isFalse();
        assertThat(useCase.unfollow("user-1", "team-1").followerCount())
                .isZero();

        ArgumentCaptor<IntegrationEvent> events =
                ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(outbox, times(2)).append(events.capture());
        assertThat(events.getAllValues())
                .extracting(event ->
                        ((TeamFollowUseCase.FollowChanged) event.payload())
                                .delta())
                .containsExactly(1, -1);
    }

    @Test
    void concurrentFollowCreatesOneRelationAndOneCounterEvent()
            throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 32; index++) {
                executor.submit(() ->
                        useCase.follow("user-1", "team-1"));
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
            )).isTrue();
        }

        assertThat(follows.count("team-1")).isEqualTo(1);
        verify(outbox, times(1)).append(any());
    }

    @Test
    void inactiveOrMissingTeamIsHidden() {
        when(teams.findById("team-1")).thenReturn(Optional.of(team(
                Team.State.SUSPENDED
        )));
        assertThatThrownBy(() -> useCase.follow("user-1", "team-1"))
                .isInstanceOf(TeamNotFoundException.class);
        when(teams.findById("team-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.status("user-1", "team-1"))
                .isInstanceOf(TeamNotFoundException.class);
    }

    @Test
    void invalidCounterDeltaIsRejected() {
        assertThatThrownBy(() ->
                new TeamFollowUseCase.FollowChanged("team-1", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Team team(Team.State state) {
        return new Team(
                "team-1",
                "lam-da",
                "Lam Da",
                "",
                "owner-1",
                state,
                NOW,
                NOW,
                0
        );
    }

    private static final class InMemoryFollows
            implements TeamFollowRepository {

        private final ConcurrentHashMap<String, TeamFollow> values =
                new ConcurrentHashMap<>();

        @Override
        public boolean insertIfAbsent(TeamFollow follow) {
            return values.putIfAbsent(follow.id(), follow) == null;
        }

        @Override
        public boolean deleteIfPresent(String teamId, String userId) {
            return values.remove(teamId + ":" + userId) != null;
        }

        @Override
        public boolean exists(String teamId, String userId) {
            return values.containsKey(teamId + ":" + userId);
        }

        @Override
        public long count(String teamId) {
            return values.values().stream()
                    .filter(follow -> follow.teamId().equals(teamId))
                    .count();
        }
    }
}
