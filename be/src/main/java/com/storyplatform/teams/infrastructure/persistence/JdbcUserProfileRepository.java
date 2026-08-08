package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.application.port.UserProfileRepository;
import com.storyplatform.teams.domain.UserProfile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcUserProfileRepository implements UserProfileRepository {

    private final JdbcClient jdbc;

    public JdbcUserProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserProfile findOrCreate(
            String userId,
            String defaultDisplayName,
            Instant now
    ) {
        jdbc.sql("""
                        INSERT INTO user_profiles (
                            user_id, display_name, bio,
                            created_at, updated_at, version
                        ) VALUES (
                            :userId, :displayName, '', :now, :now, 0
                        )
                        ON DUPLICATE KEY UPDATE user_id = user_id
                        """)
                .param("userId", userId)
                .param("displayName", defaultDisplayName)
                .param("now", now)
                .update();
        return findByUserId(userId).orElseThrow();
    }

    @Override
    public Optional<UserProfile> findByUserId(String userId) {
        return jdbc.sql("""
                        SELECT user_id, display_name, bio, avatar_media_id,
                               created_at, updated_at, version
                        FROM user_profiles
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query(JdbcUserProfileRepository::mapProfile)
                .optional();
    }

    @Override
    public UpdateResult update(
            String userId,
            long expectedVersion,
            String displayName,
            String bio,
            String avatarMediaId,
            Instant now
    ) {
        int updated = jdbc.sql("""
                        UPDATE user_profiles
                        SET display_name = :displayName,
                            bio = :bio,
                            avatar_media_id = :avatarMediaId,
                            updated_at = :now,
                            version = version + 1
                        WHERE user_id = :userId
                          AND version = :expectedVersion
                        """)
                .param("displayName", displayName)
                .param("bio", bio)
                .param("avatarMediaId", avatarMediaId)
                .param("now", now)
                .param("userId", userId)
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated == 1) {
            return UpdateResult.UPDATED;
        }
        boolean exists = jdbc.sql("""
                        SELECT COUNT(*)
                        FROM user_profiles
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query(Long.class)
                .single() == 1;
        return exists ? UpdateResult.VERSION_CONFLICT : UpdateResult.NOT_FOUND;
    }

    private static UserProfile mapProfile(
            ResultSet result,
            int rowNumber
    ) throws SQLException {
        return new UserProfile(
                result.getString("user_id"),
                result.getString("display_name"),
                result.getString("bio"),
                result.getString("avatar_media_id"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("version")
        );
    }
}
