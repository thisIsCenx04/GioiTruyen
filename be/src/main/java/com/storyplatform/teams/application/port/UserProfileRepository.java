package com.storyplatform.teams.application.port;

import com.storyplatform.teams.domain.UserProfile;

import java.time.Instant;
import java.util.Optional;

public interface UserProfileRepository {

    UserProfile findOrCreate(
            String userId,
            String defaultDisplayName,
            Instant now
    );

    Optional<UserProfile> findByUserId(String userId);

    UpdateResult update(
            String userId,
            long expectedVersion,
            String displayName,
            String bio,
            String avatarMediaId,
            Instant now
    );

    enum UpdateResult {
        UPDATED,
        VERSION_CONFLICT,
        NOT_FOUND
    }
}
