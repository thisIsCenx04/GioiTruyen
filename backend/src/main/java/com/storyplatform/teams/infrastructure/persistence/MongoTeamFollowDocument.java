package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.domain.TeamFollow;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoTeamFollowDocument.COLLECTION)
public record MongoTeamFollowDocument(
        @Id String id,
        String teamId,
        String userId,
        Instant createdAt
) {
    public static final String COLLECTION = "team_follows";

    static MongoTeamFollowDocument from(TeamFollow follow) {
        return new MongoTeamFollowDocument(
                follow.id(),
                follow.teamId(),
                follow.userId(),
                follow.createdAt()
        );
    }
}
