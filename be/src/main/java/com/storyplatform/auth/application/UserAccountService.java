package com.storyplatform.auth.application;

import com.storyplatform.auth.application.dto.UpdateUserProfileRequest;
import com.storyplatform.auth.application.dto.UserDto;
import com.storyplatform.auth.application.dto.UserProfileResponse;
import com.storyplatform.auth.domain.User;
import com.storyplatform.auth.infrastructure.UserRepository;
import com.storyplatform.shared.api.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final NamedParameterJdbcTemplate jdbc;

    public UserAccountService(UserRepository userRepository, NamedParameterJdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public UserDto me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user.not_found", "User not found", "User not found"));
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(UUID userId) {
        return jdbc.query(
                        """
                                SELECT bio, cover_url, gender, birthday, website_url, updated_at
                                FROM user_profiles
                                WHERE user_id = :userId
                                LIMIT 1
                                """,
                        Map.of("userId", userId.toString()),
                        (rs, rowNum) -> profile(rs)
                ).stream()
                .findFirst()
                .orElseGet(() -> new UserProfileResponse(null, null, null, null, null, null));
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateUserProfileRequest request) {
        Instant now = Instant.now();
        jdbc.update(
                """
                        INSERT INTO user_profiles (user_id, bio, cover_url, gender, birthday, website_url, updated_at)
                        VALUES (:userId, :bio, :coverUrl, :gender, :birthday, :websiteUrl, :updatedAt)
                        ON DUPLICATE KEY UPDATE
                            bio = VALUES(bio),
                            cover_url = VALUES(cover_url),
                            gender = VALUES(gender),
                            birthday = VALUES(birthday),
                            website_url = VALUES(website_url),
                            updated_at = VALUES(updated_at)
                        """,
                new MapSqlParameterSource()
                        .addValue("userId", userId.toString())
                        .addValue("bio", request.bio())
                        .addValue("coverUrl", request.coverUrl())
                        .addValue("gender", request.gender())
                        .addValue("birthday", request.birthday())
                        .addValue("websiteUrl", request.websiteUrl())
                        .addValue("updatedAt", now)
        );
        return profile(userId);
    }

    private UserProfileResponse profile(ResultSet rs) throws SQLException {
        return new UserProfileResponse(
                rs.getString("bio"),
                rs.getString("cover_url"),
                rs.getString("gender"),
                rs.getDate("birthday") == null ? null : rs.getDate("birthday").toLocalDate(),
                rs.getString("website_url"),
                rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant()
        );
    }
}
