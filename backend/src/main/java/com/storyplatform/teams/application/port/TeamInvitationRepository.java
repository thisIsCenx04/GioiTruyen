package com.storyplatform.teams.application.port;

import com.storyplatform.teams.domain.TeamInvitation;

import java.time.Instant;
import java.util.Optional;

public interface TeamInvitationRepository {

    void insert(TeamInvitation invitation);

    Optional<TeamInvitation> findByIdempotencyKey(
            String teamId,
            String key
    );

    Optional<TeamInvitation> findByTokenHash(String tokenHash);

    boolean accept(String invitationId, long version, Instant acceptedAt);
}
