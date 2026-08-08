package com.storyplatform.discovery.api;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PromotionController {

    private static final CacheControl PUBLIC_CACHE =
            CacheControl.maxAge(Duration.ofSeconds(60))
                    .cachePublic()
                    .staleWhileRevalidate(Duration.ofMinutes(5));

    private final JdbcClient jdbc;

    public PromotionController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/promotions/home")
    public ResponseEntity<List<PromotedStoryResponse>> homePromotions() {
        List<PromotedStoryResponse> stories = jdbc.sql("""
                        SELECT b.id AS booking_id,
                               b.slot_position,
                               b.tag_label,
                               s.id AS story_id,
                               s.team_id,
                               t.name AS team_name,
                               s.slug,
                               s.title,
                               s.cover_asset_id,
                               s.published_at,
                               COALESCE(m.view_count, 0) AS view_count,
                               COALESCE(m.save_count, 0) AS save_count
                        FROM story_promotion_bookings b
                        JOIN stories s ON s.id = b.story_id
                        JOIN teams t ON t.id = s.team_id
                        LEFT JOIN story_engagement_metrics m ON m.story_id = s.id
                        WHERE b.state = 'ACTIVE'
                          AND b.starts_at <= CURRENT_TIMESTAMP(6)
                          AND b.ends_at > CURRENT_TIMESTAMP(6)
                          AND s.workflow_status = 'PUBLISHED'
                        ORDER BY b.slot_position ASC
                        LIMIT 12
                        """)
                .query((result, rowNumber) -> new PromotedStoryResponse(
                        result.getString("booking_id"),
                        result.getInt("slot_position"),
                        result.getString("tag_label"),
                        new HomePromotedStory(
                                result.getString("story_id"),
                                result.getString("team_id"),
                                result.getString("team_name"),
                                result.getString("slug"),
                                result.getString("title"),
                                result.getString("cover_asset_id"),
                                timestamp(result, "published_at"),
                                result.getLong("view_count"),
                                result.getLong("save_count")
                        )
                ))
                .list();
        return ResponseEntity.ok()
                .cacheControl(PUBLIC_CACHE)
                .body(stories);
    }

    private static String timestamp(ResultSet result, String column)
            throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }

    public record PromotedStoryResponse(
            String bookingId,
            int slotPosition,
            String tagLabel,
            HomePromotedStory story
    ) {
    }

    public record HomePromotedStory(
            String id,
            String teamId,
            String teamName,
            String slug,
            String title,
            String coverAssetId,
            String publishedAt,
            long viewCount,
            long saveCount
    ) {
    }
}
