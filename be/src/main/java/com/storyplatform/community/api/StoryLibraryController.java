package com.storyplatform.community.api;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

@RestController
public class StoryLibraryController {

    private final JdbcClient jdbc;
    private final Clock clock;

    public StoryLibraryController(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Clock.systemUTC();
    }

    @GetMapping("/me/library")
    public List<LibraryStory> library(@AuthenticationPrincipal Jwt jwt) {
        return jdbc.sql("""
                        SELECT s.id, s.team_id, s.slug, s.title,
                               s.cover_asset_id, s.published_at,
                               COALESCE(m.view_count, 0) AS view_count,
                               COALESCE(m.save_count, 0) AS save_count
                        FROM story_library_entries l
                        JOIN stories s ON s.id = l.story_id
                        LEFT JOIN story_engagement_metrics m
                          ON m.story_id = s.id
                        WHERE l.user_id = :userId
                          AND s.workflow_status = 'PUBLISHED'
                        ORDER BY l.saved_at DESC
                        LIMIT 100
                        """)
                .param("userId", jwt.getSubject())
                .query((result, rowNumber) -> new LibraryStory(
                        result.getString("id"),
                        result.getString("team_id"),
                        result.getString("slug"),
                        result.getString("title"),
                        result.getString("cover_asset_id"),
                        result.getTimestamp("published_at").toInstant()
                                .toString(),
                        result.getLong("view_count"),
                        result.getLong("save_count")
                ))
                .list();
    }

    @GetMapping("/stories/{storyId}/favorite")
    public RelationView favoriteStatus(
            @PathVariable String storyId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return relation(storyId, jwt.getSubject(), true);
    }

    @PutMapping("/stories/{storyId}/favorite")
    @Transactional
    public RelationView favorite(
            @PathVariable String storyId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        jdbc.sql("""
                INSERT INTO story_library_entries (
                    user_id, story_id, saved_at
                ) VALUES (:userId, :storyId, :now)
                ON DUPLICATE KEY UPDATE user_id = user_id
                """)
                .param("userId", jwt.getSubject())
                .param("storyId", storyId)
                .param("now", clock.instant())
                .update();
        synchronizeSaveCount(storyId);
        return relation(storyId, jwt.getSubject(), true);
    }

    @DeleteMapping("/stories/{storyId}/favorite")
    @Transactional
    public RelationView removeFavorite(
            @PathVariable String storyId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        jdbc.sql("""
                DELETE FROM story_library_entries
                WHERE user_id = :userId AND story_id = :storyId
                """)
                .param("userId", jwt.getSubject())
                .param("storyId", storyId)
                .update();
        synchronizeSaveCount(storyId);
        return relation(storyId, jwt.getSubject(), true);
    }

    @GetMapping("/stories/{storyId}/follow")
    public RelationView followStatus(
            @PathVariable String storyId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return relation(storyId, jwt.getSubject(), false);
    }

    @PutMapping("/stories/{storyId}/follow")
    public RelationView follow(
            @PathVariable String storyId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        jdbc.sql("""
                INSERT INTO story_follows (
                    user_id, story_id, followed_at
                ) VALUES (:userId, :storyId, :now)
                ON DUPLICATE KEY UPDATE user_id = user_id
                """)
                .param("userId", jwt.getSubject())
                .param("storyId", storyId)
                .param("now", clock.instant())
                .update();
        return relation(storyId, jwt.getSubject(), false);
    }

    @DeleteMapping("/stories/{storyId}/follow")
    public RelationView removeFollow(
            @PathVariable String storyId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        jdbc.sql("""
                DELETE FROM story_follows
                WHERE user_id = :userId AND story_id = :storyId
                """)
                .param("userId", jwt.getSubject())
                .param("storyId", storyId)
                .update();
        return relation(storyId, jwt.getSubject(), false);
    }

    private RelationView relation(
            String storyId,
            String userId,
            boolean favorite
    ) {
        String table = favorite
                ? "story_library_entries"
                : "story_follows";
        long count = jdbc.sql("SELECT COUNT(*) FROM " + table
                        + " WHERE story_id = :storyId")
                .param("storyId", storyId)
                .query(Long.class)
                .single();
        boolean active = jdbc.sql("SELECT COUNT(*) FROM " + table
                        + " WHERE story_id = :storyId AND user_id = :userId")
                .param("storyId", storyId)
                .param("userId", userId)
                .query(Long.class)
                .single() > 0;
        return new RelationView(
                storyId,
                favorite ? "FAVORITE" : "FOLLOW",
                active,
                count
        );
    }

    private void synchronizeSaveCount(String storyId) {
        jdbc.sql("""
                UPDATE story_engagement_metrics
                SET save_count = (
                        SELECT COUNT(*)
                        FROM story_library_entries
                        WHERE story_id = :storyId
                    ),
                    updated_at = :now
                WHERE story_id = :storyId
                """)
                .param("storyId", storyId)
                .param("now", clock.instant())
                .update();
    }

    public record RelationView(
            String storyId,
            String type,
            boolean active,
            long count
    ) {
    }

    public record LibraryStory(
            String id,
            String teamId,
            String slug,
            String title,
            String coverAssetId,
            String publishedAt,
            long viewCount,
            long saveCount
    ) {
    }
}
