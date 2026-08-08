package com.storyplatform.teams.infrastructure;

import com.storyplatform.teams.application.TeamMembershipOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class TransactionalTeamMembershipOperations
        implements TeamMembershipOperations {

    private final TeamMembershipOperations delegate;

    public TransactionalTeamMembershipOperations(
            TeamMembershipOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembershipView> list(String actorId, String teamId) {
        return delegate.list(actorId, teamId);
    }

    @Override
    @Transactional
    public MembershipView invite(
            String actorId,
            String teamId,
            String targetUserId,
            Set<String> permissions,
            String idempotencyKey
    ) {
        return delegate.invite(
                actorId,
                teamId,
                targetUserId,
                permissions,
                idempotencyKey
        );
    }

    @Override
    @Transactional
    public MembershipView accept(String actorId, String rawToken) {
        return delegate.accept(actorId, rawToken);
    }

    @Override
    @Transactional
    public MembershipView updatePermissions(
            String actorId,
            String teamId,
            String targetUserId,
            long version,
            Set<String> permissions
    ) {
        return delegate.updatePermissions(
                actorId,
                teamId,
                targetUserId,
                version,
                permissions
        );
    }

    @Override
    @Transactional
    public void remove(
            String actorId,
            String teamId,
            String targetUserId,
            long version
    ) {
        delegate.remove(actorId, teamId, targetUserId, version);
    }
}
