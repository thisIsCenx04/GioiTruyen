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
public class StoryDiscoveryController {

    private static final CacheControl PUBLIC_CACHE =
            CacheControl.maxAge(Duration.ofSeconds(60))
                    .cachePublic()
                    .staleWhileRevalidate(Duration.ofMinutes(5));

    private final JdbcClient jdbc;

    public StoryDiscoveryController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/stories/sections")
    public ResponseEntity<List<TaggedStorySectionResponse>> storySections() {
        List<TaggedStorySectionResponse> sections = List.of(
                new TaggedStorySectionResponse(
                        "exclusive",
                        "EXCLUSIVE",
                        "Truyện độc quyền",
                        storiesByLabel("EXCLUSIVE", true)
                ),
                new TaggedStorySectionResponse(
                        "new-release",
                        "NEW_RELEASE",
                        "Truyện mới ra lò",
                        storiesByLabel("NEW_RELEASE", false)
                ),
                new TaggedStorySectionResponse(
                        "recent-update",
                        "RECENT_UPDATE",
                        "Truyện mới cập nhật",
                        storiesByLabel("RECENT_UPDATE", true)
                ),
                new TaggedStorySectionResponse(
                        "original",
                        "ORIGINAL",
                        "Truyện sáng tác",
                        storiesByLabel("ORIGINAL", false)
                ),
                new TaggedStorySectionResponse(
                        "completed",
                        "COMPLETED",
                        "Truyện hoàn thành",
                        storiesByLabel("COMPLETED", false)
                )
        );
        return ResponseEntity.ok()
                .cacheControl(PUBLIC_CACHE)
                .body(sections);
    }

    @GetMapping("/rankings/boards")
    public ResponseEntity<List<RankingBoardResponse>> rankingBoards() {
        List<RankingBoardResponse> boards = List.of(
                new RankingBoardResponse(
                        "gold",
                        "Thánh bảng/Mâm vàng",
                        "Top doanh thu và ủng hộ",
                        "XU",
                        rankedStories("donation_xu")
                ),
                new RankingBoardResponse(
                        "recommendations",
                        "Top đề cử",
                        "Truyện được reader đề cử nhiều nhất",
                        "đề cử",
                        rankedStories("recommendation_count")
                ),
                new RankingBoardResponse(
                        "views",
                        "Top lượt xem",
                        "Truyện có lượt đọc cao nhất",
                        "lượt xem",
                        rankedStories("view_count")
                )
        );
        return ResponseEntity.ok()
                .cacheControl(PUBLIC_CACHE)
                .body(boards);
    }

    private List<HomeStorySummaryResponse> storiesByLabel(
            String label,
            boolean orderByLatestChapter
    ) {
        String sortColumn = orderByLatestChapter
                ? "COALESCE(latest.latest_chapter_at, s.updated_at, s.published_at)"
                : "COALESCE(s.published_at, s.created_at)";
        return jdbc.sql("""
                        SELECT s.id,
                               s.team_id,
                               s.slug,
                               s.title,
                               s.cover_asset_id,
                               s.published_at,
                               COALESCE(m.view_count, 0) AS view_count,
                               COALESCE(m.save_count, 0) AS save_count
                        FROM story_labels l
                        JOIN stories s ON s.id = l.story_id
                        LEFT JOIN story_engagement_metrics m ON m.story_id = s.id
                        LEFT JOIN (
                            SELECT story_id,
                                   MAX(COALESCE(published_at, updated_at)) AS latest_chapter_at
                            FROM chapters
                            WHERE workflow_status = 'PUBLISHED'
                            GROUP BY story_id
                        ) latest ON latest.story_id = s.id
                        WHERE l.label = :label
                          AND s.workflow_status = 'PUBLISHED'
                        ORDER BY %s DESC, s.title ASC
                        LIMIT 8
                        """.formatted(sortColumn))
                .param("label", label)
                .query((result, rowNumber) -> story(result))
                .list();
    }

    private List<RankingStoryResponse> rankedStories(String metricColumn) {
        return jdbc.sql("""
                        SELECT s.id,
                               s.team_id,
                               s.slug,
                               s.title,
                               s.cover_asset_id,
                               s.published_at,
                               COALESCE(m.view_count, 0) AS view_count,
                               COALESCE(m.save_count, 0) AS save_count,
                               COALESCE(m.%s, 0) AS metric_value
                        FROM stories s
                        LEFT JOIN story_engagement_metrics m ON m.story_id = s.id
                        WHERE s.workflow_status = 'PUBLISHED'
                        ORDER BY COALESCE(m.%s, 0) DESC, s.title ASC
                        LIMIT 10
                        """.formatted(metricColumn, metricColumn))
                .query((result, rowNumber) -> new RankingStoryResponse(
                        rowNumber + 1,
                        result.getLong("metric_value"),
                        story(result)
                ))
                .list();
    }

    private static HomeStorySummaryResponse story(ResultSet result)
            throws SQLException {
        return new HomeStorySummaryResponse(
                result.getString("id"),
                result.getString("team_id"),
                result.getString("slug"),
                result.getString("title"),
                result.getString("cover_asset_id"),
                timestamp(result, "published_at"),
                result.getLong("view_count"),
                result.getLong("save_count")
        );
    }

    private static String timestamp(ResultSet result, String column)
            throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }

    public record TaggedStorySectionResponse(
            String id,
            String tag,
            String title,
            List<HomeStorySummaryResponse> stories
    ) {
    }

    public record RankingBoardResponse(
            String id,
            String title,
            String subtitle,
            String unit,
            List<RankingStoryResponse> stories
    ) {
    }

    public record RankingStoryResponse(
            int rank,
            long metricValue,
            HomeStorySummaryResponse story
    ) {
    }

    public record HomeStorySummaryResponse(
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
