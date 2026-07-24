package com.storyplatform.teams.application.port;

import com.storyplatform.teams.domain.TeamFollow;

public interface TeamFollowRepository {

    boolean insertIfAbsent(TeamFollow follow);

    boolean deleteIfPresent(String teamId, String userId);

    boolean exists(String teamId, String userId);

    long count(String teamId);
}
