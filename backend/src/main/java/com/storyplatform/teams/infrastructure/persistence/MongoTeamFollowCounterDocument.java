package com.storyplatform.teams.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoTeamFollowCounterDocument.COLLECTION)
public record MongoTeamFollowCounterDocument(
        @Id String teamId,
        long followerCount,
        Instant updatedAt
) {
    public static final String COLLECTION = "team_follow_counters";
}
