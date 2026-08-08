package com.storyplatform.teams.application;

import java.util.List;

public interface TeamOperations {

    TeamView create(
            String actorId,
            String slug,
            String name,
            String description
    );

    TeamView get(String teamId);

    List<TeamView> list(int limit);

    TeamView update(
            String actorId,
            String teamId,
            long version,
            String name,
            String description
    );

    record TeamView(
            String id,
            String slug,
            String name,
            String description,
            String state,
            long version
    ) {
    }
}
