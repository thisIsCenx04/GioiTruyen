package com.storyplatform.teams.infrastructure;

import com.storyplatform.teams.application.TeamOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

public class TransactionalTeamOperations implements TeamOperations {

    private final TeamOperations delegate;

    public TransactionalTeamOperations(TeamOperations delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public TeamView create(
            String actorId,
            String slug,
            String name,
            String description
    ) {
        return delegate.create(actorId, slug, name, description);
    }

    @Override
    @Transactional(readOnly = true)
    public TeamView get(String teamId) {
        return delegate.get(teamId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamView> list(int limit) {
        return delegate.list(limit);
    }

    @Override
    @Transactional
    public TeamView update(
            String actorId,
            String teamId,
            long version,
            String name,
            String description
    ) {
        return delegate.update(
                actorId,
                teamId,
                version,
                name,
                description
        );
    }
}
