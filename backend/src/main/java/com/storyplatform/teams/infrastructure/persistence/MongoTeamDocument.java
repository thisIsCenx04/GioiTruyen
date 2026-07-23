package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.domain.Team;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoTeamDocument.COLLECTION)
public record MongoTeamDocument(
        @Id String id,
        String slug,
        String name,
        String description,
        String ownerUserId,
        Team.State state,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static final String COLLECTION = "teams";

    Team toDomain() {
        return new Team(
                id,
                slug,
                name,
                description,
                ownerUserId,
                state,
                createdAt,
                updatedAt,
                version
        );
    }
}
