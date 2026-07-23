package com.storyplatform.teams.application.port;

import com.storyplatform.teams.domain.TeamMembership;

public interface TeamMembershipRepository {

    void insertOwner(TeamMembership membership);
}
