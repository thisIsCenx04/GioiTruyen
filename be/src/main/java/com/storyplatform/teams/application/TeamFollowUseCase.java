package com.storyplatform.teams.application;

import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.port.TeamFollowRepository;
import com.storyplatform.teams.application.port.TeamRepository;
import com.storyplatform.teams.domain.Team;
import com.storyplatform.teams.domain.TeamFollow;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TeamFollowUseCase implements TeamFollowOperations {

    public static final String EVENT_TYPE = "teams.follow.changed";

    private final TeamRepository teams;
    private final TeamFollowRepository follows;
    private final OutboxAppender outbox;
    private final Clock clock;

    public TeamFollowUseCase(
            TeamRepository teams,
            TeamFollowRepository follows,
            OutboxAppender outbox,
            Clock clock
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.follows = Objects.requireNonNull(follows, "follows");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public FollowView status(String actorId, String teamId) {
        requireActiveTeam(teamId);
        return view(actorId, teamId);
    }

    @Override
    public FollowView follow(String actorId, String teamId) {
        requireActiveTeam(teamId);
        Instant now = clock.instant();
        boolean created = follows.insertIfAbsent(
                TeamFollow.create(teamId, actorId, now)
        );
        if (created) {
            append(actorId, teamId, 1, now);
        }
        return new FollowView(teamId, true, follows.count(teamId));
    }

    @Override
    public FollowView unfollow(String actorId, String teamId) {
        requireActiveTeam(teamId);
        Instant now = clock.instant();
        boolean deleted = follows.deleteIfPresent(teamId, actorId);
        if (deleted) {
            append(actorId, teamId, -1, now);
        }
        return new FollowView(teamId, false, follows.count(teamId));
    }

    private FollowView view(String actorId, String teamId) {
        return new FollowView(
                teamId,
                follows.exists(teamId, actorId),
                follows.count(teamId)
        );
    }

    private void requireActiveTeam(String teamId) {
        teams.findById(teamId)
                .filter(team -> team.state() == Team.State.ACTIVE)
                .orElseThrow(TeamNotFoundException::new);
    }

    private void append(
            String actorId,
            String teamId,
            int delta,
            Instant now
    ) {
        outbox.append(new IntegrationEvent(
                UUID.randomUUID(),
                EVENT_TYPE,
                1,
                now,
                UUID.randomUUID().toString(),
                "team_follow",
                teamId + ":" + actorId,
                actorId,
                teamId,
                new FollowChanged(teamId, delta)
        ));
    }

    public record FollowChanged(String teamId, int delta) {
        public FollowChanged {
            if (delta != 1 && delta != -1) {
                throw new IllegalArgumentException(
                        "follow delta must be 1 or -1"
                );
            }
        }
    }
}
