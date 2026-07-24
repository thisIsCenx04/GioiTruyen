package com.storyplatform.teams.infrastructure;

import com.storyplatform.teams.application.port.TeamFollowRepository;

import java.util.Objects;

public final class TeamFollowCounterReconciler {

    private final TeamFollowRepository follows;
    private final TeamFollowCounterStore counters;

    public TeamFollowCounterReconciler(
            TeamFollowRepository follows,
            TeamFollowCounterStore counters
    ) {
        this.follows = Objects.requireNonNull(follows, "follows");
        this.counters = Objects.requireNonNull(counters, "counters");
    }

    public long reconcile(String teamId) {
        long count = follows.count(teamId);
        counters.reconcile(teamId, count);
        return count;
    }
}
