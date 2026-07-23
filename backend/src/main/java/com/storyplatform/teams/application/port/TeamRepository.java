package com.storyplatform.teams.application.port;

import com.storyplatform.teams.domain.Team;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TeamRepository {

    boolean insertIfSlugAvailable(Team team);

    Optional<Team> findById(String teamId);

    List<Team> listActive(int limit);

    UpdateResult updateOwned(
            String teamId,
            String ownerUserId,
            long version,
            String name,
            String description,
            Instant now
    );

    enum UpdateResult {
        UPDATED,
        VERSION_CONFLICT,
        NOT_OWNED_OR_NOT_FOUND
    }
}
