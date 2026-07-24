package com.storyplatform.teams.infrastructure;

import com.storyplatform.teams.application.TeamFollowOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalTeamFollowOperations
        implements TeamFollowOperations {

    private final TeamFollowOperations delegate;

    public TransactionalTeamFollowOperations(
            TeamFollowOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional(readOnly = true)
    public FollowView status(String actorId, String teamId) {
        return delegate.status(actorId, teamId);
    }

    @Override
    @Transactional
    public FollowView follow(String actorId, String teamId) {
        return delegate.follow(actorId, teamId);
    }

    @Override
    @Transactional
    public FollowView unfollow(String actorId, String teamId) {
        return delegate.unfollow(actorId, teamId);
    }
}
