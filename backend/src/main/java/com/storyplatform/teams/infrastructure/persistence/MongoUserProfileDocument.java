package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.domain.UserProfile;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoUserProfileDocument.COLLECTION)
public record MongoUserProfileDocument(
        @Id String userId,
        String displayName,
        String bio,
        String avatarMediaId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static final String COLLECTION = "user_profiles";

    UserProfile toDomain() {
        return new UserProfile(
                userId,
                displayName,
                bio,
                avatarMediaId,
                createdAt,
                updatedAt,
                version
        );
    }
}
