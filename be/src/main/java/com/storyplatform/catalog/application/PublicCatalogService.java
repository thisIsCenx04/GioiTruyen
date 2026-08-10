package com.storyplatform.catalog.application;

import com.storyplatform.catalog.application.dto.CatalogDtos;
import com.storyplatform.shared.api.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PublicCatalogService {

    private static final int DEFAULT_LIMIT = 20;
    /** Number of "bố cáo" slots shown on the home page. */
    private static final int PROMOTED_SLOT_COUNT = 12;
    private static final String PUBLISHED_STORY_FILTER = "s.status = 'PUBLISHED' AND s.published_at IS NOT NULL";
    private static final String STORY_SUMMARY_SELECT = """
            SELECT s.id, s.team_id, t.name AS team_name, s.slug, s.title, s.cover_url,
                   s.published_at, s.view_count_cache, s.favorite_count_cache
            FROM stories s
            JOIN teams t ON t.id = s.team_id
            WHERE %s
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PublicCatalogService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CatalogDtos.HomeResponse home(String locale) {
        return new CatalogDtos.HomeResponse(
                locale == null || locale.isBlank() ? "vi-VN" : locale,
                "seed-2026-08-09",
                Instant.now().toString(),
                List.of(
                        new CatalogDtos.HomeSection(
                                "latest",
                                "LATEST",
                                "Truyện mới cập nhật",
                                summaries("s.last_chapter_at DESC, s.published_at DESC", 8)
                        ),
                        new CatalogDtos.HomeSection(
                                "completed",
                                "COMPLETED",
                                "Truyện full",
                                summaries("s.published_at DESC", 8, "s.progress_status = 'COMPLETED'")
                        ),
                        new CatalogDtos.HomeSection(
                                "original",
                                "ORIGINAL",
                                "Đề cử hôm nay",
                                summaries("s.recommendation_gem_cache DESC, s.view_count_cache DESC", 8)
                        )
                )
        );
    }

    public CatalogDtos.CategoryTaxonomy categories() {
        List<CatalogDtos.CategoryItem> categories = jdbc.query(
                """
                        SELECT id, slug, name
                        FROM genres
                        WHERE is_active = TRUE
                        ORDER BY name ASC
                        """,
                Map.of(),
                (rs, rowNum) -> new CatalogDtos.CategoryItem(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("name")
                )
        );
        return new CatalogDtos.CategoryTaxonomy(
                "seed-2026-08-09",
                List.of(new CatalogDtos.CategoryGroup("genres", "Thể loại", categories))
        );
    }

    /**
     * Serves the paid "bố cáo" slots, earliest booking first so teams that bought
     * in sooner keep the better position. Falls back to popular stories while no
     * booking is running, so the home page never renders an empty shelf.
     */
    public List<CatalogDtos.PromotedHomeStory> promotedHome() {
        List<CatalogDtos.PromotedHomeStory> booked = jdbc.query(
                """
                        SELECT p.id AS promotion_id, p.tag_label,
                               s.id, s.team_id, t.name AS team_name, s.slug, s.title, s.cover_url,
                               s.published_at, s.view_count_cache, s.favorite_count_cache
                        FROM story_promotions p
                        JOIN stories s ON s.id = p.story_id
                        JOIN teams t ON t.id = s.team_id
                        WHERE p.status = 'ACTIVE'
                          AND p.starts_at <= NOW(3)
                          AND p.ends_at > NOW(3)
                          AND %s
                        ORDER BY p.created_at ASC
                        LIMIT :limit
                        """.formatted(PUBLISHED_STORY_FILTER),
                new MapSqlParameterSource("limit", PROMOTED_SLOT_COUNT),
                (rs, rowNum) -> new CatalogDtos.PromotedHomeStory(
                        rs.getString("promotion_id"),
                        rowNum + 1,
                        rs.getString("tag_label"),
                        summary(rs)
                )
        );

        if (!booked.isEmpty()) {
            return booked;
        }

        List<CatalogDtos.HomeStorySummary> stories =
                summaries("s.recommendation_gem_cache DESC, s.view_count_cache DESC", PROMOTED_SLOT_COUNT);
        return IntStream.range(0, stories.size())
                .mapToObj(index -> new CatalogDtos.PromotedHomeStory(
                        "suggested-" + stories.get(index).id(),
                        index + 1,
                        index == 0 ? "Hot" : "Đề cử",
                        stories.get(index)
                ))
                .toList();
    }

    public List<CatalogDtos.TaggedStorySection> storySections() {
        return List.of(
                new CatalogDtos.TaggedStorySection(
                        "new-release",
                        "NEW_RELEASE",
                        "Truyện mới đăng",
                        summaries("s.published_at DESC", 8)
                ),
                new CatalogDtos.TaggedStorySection(
                        "recent-update",
                        "RECENT_UPDATE",
                        "Vừa cập nhật chương",
                        summaries("s.last_chapter_at DESC, s.updated_at DESC", 8)
                ),
                new CatalogDtos.TaggedStorySection(
                        "completed",
                        "COMPLETED",
                        "Đã hoàn thành",
                        summaries("s.published_at DESC", 8, "s.progress_status = 'COMPLETED'")
                )
        );
    }

    public List<CatalogDtos.RankingBoard> rankingBoards() {
        return List.of(
                rankingBoard("gold", "Bảng doanh thu", "Truyện có doanh thu xu cao", "xu", "COIN_REVENUE"),
                rankingBoard("recommendations", "Bảng đề cử", "Truyện được tặng ngọc nhiều", "ngọc", "GEM_RECOMMENDATION"),
                rankingBoard("views", "Bảng lượt xem", "Truyện được đọc nhiều", "lượt", "VIEWS")
        );
    }

    public List<CatalogDtos.HomeStorySummary> categoryStories(String slug) {
        return jdbc.query(
                STORY_SUMMARY_SELECT.formatted(PUBLISHED_STORY_FILTER + """
                        AND EXISTS (
                            SELECT 1
                            FROM story_genres sg
                            JOIN genres g ON g.id = sg.genre_id
                            WHERE sg.story_id = s.id AND g.slug = :slug
                        )
                        ORDER BY s.published_at DESC
                        LIMIT :limit
                        """),
                new MapSqlParameterSource()
                        .addValue("slug", slug)
                        .addValue("limit", DEFAULT_LIMIT),
                (rs, rowNum) -> summary(rs)
        );
    }

    public CatalogDtos.PublicStory story(String identifier) {
        return jdbc.query(
                        """
                                SELECT id, team_id, slug, title, short_description, description, original_title,
                                       progress_status, published_at, updated_at
                                FROM stories
                                WHERE (id = :identifier OR slug = :identifier)
                                  AND status = 'PUBLISHED'
                                  AND published_at IS NOT NULL
                                LIMIT 1
                                """,
                        Map.of("identifier", identifier),
                        (rs, rowNum) -> new CatalogDtos.PublicStory(
                                rs.getString("id"),
                                rs.getString("team_id"),
                                rs.getString("slug"),
                                rs.getString("title"),
                                firstText(rs.getString("description"), rs.getString("short_description")),
                                categoryIds(rs.getString("id")),
                                rs.getString("original_title") == null ? "ORIGINAL" : "TRANSLATED",
                                "vi-VN",
                                mapCompletionStatus(rs.getString("progress_status")),
                                instantString(rs, "published_at"),
                                instantString(rs, "updated_at"),
                                1
                        )
                ).stream()
                .findFirst()
                .orElseThrow(() -> notFound("Story not found"));
    }

    public CatalogDtos.ChapterPage chapters(String identifier, int limit) {
        String storyId = resolveStoryId(identifier);
        List<CatalogDtos.PublicChapter> items = jdbc.query(
                """
                        SELECT id, story_id, chapter_number, slug, title, published_at
                        FROM chapters
                        WHERE story_id = :storyId
                          AND status = 'PUBLISHED'
                          AND published_at IS NOT NULL
                        ORDER BY chapter_number ASC
                        LIMIT :limit
                        """,
                new MapSqlParameterSource()
                        .addValue("storyId", storyId)
                        .addValue("limit", Math.max(1, Math.min(limit, 100))),
                (rs, rowNum) -> chapter(rs)
        );
        return new CatalogDtos.ChapterPage(items, null, false);
    }

    public CatalogDtos.PublishedChapterDetail chapter(String chapterId) {
        return jdbc.query(
                        """
                                SELECT id, story_id, chapter_number, slug, title, content, published_at, updated_at
                                FROM chapters
                                WHERE id = :chapterId
                                  AND status = 'PUBLISHED'
                                  AND published_at IS NOT NULL
                                LIMIT 1
                                """,
                        Map.of("chapterId", chapterId),
                        (rs, rowNum) -> {
                            String content = rs.getString("content");
                            return new CatalogDtos.PublishedChapterDetail(
                                    rs.getString("id"),
                                    rs.getString("story_id"),
                                    rs.getBigDecimal("chapter_number"),
                                    rs.getString("slug"),
                                    rs.getString("title"),
                                    instantString(rs, "published_at"),
                                    1,
                                    rs.getString("id") + "-r1",
                                    1,
                                    contentHtml(content),
                                    wordCount(content),
                                    instantString(rs, "updated_at"),
                                    adjacentChapter(rs.getString("story_id"), rs.getBigDecimal("chapter_number"), "<"),
                                    adjacentChapter(rs.getString("story_id"), rs.getBigDecimal("chapter_number"), ">")
                            );
                        }
                ).stream()
                .findFirst()
                .orElseThrow(() -> notFound("Chapter not found"));
    }

    public CatalogDtos.SearchResponse search(String query, int limit) {
        long start = System.currentTimeMillis();
        List<CatalogDtos.SearchHit> items = searchSummaries(query, limit).stream()
                .map(story -> new CatalogDtos.SearchHit(story, 1.0, List.of(story.title())))
                .toList();
        return new CatalogDtos.SearchResponse(items, null, false, Map.of(), System.currentTimeMillis() - start);
    }

    public CatalogDtos.SuggestionResponse suggestions(String query, int limit) {
        List<CatalogDtos.SuggestionItem> items = searchSummaries(query, limit).stream()
                .map(story -> new CatalogDtos.SuggestionItem(story.id(), story.slug(), story.title(), story.coverAssetId()))
                .toList();
        return new CatalogDtos.SuggestionResponse(items, null, false);
    }

    private List<CatalogDtos.HomeStorySummary> summaries(String orderBy, int limit) {
        return summaries(orderBy, limit, null);
    }

    private List<CatalogDtos.HomeStorySummary> summaries(String orderBy, int limit, String extraFilter) {
        String filter = PUBLISHED_STORY_FILTER + (extraFilter == null ? "" : " AND " + extraFilter);
        return jdbc.query(
                (STORY_SUMMARY_SELECT + " ORDER BY " + orderBy + " LIMIT :limit").formatted(filter),
                new MapSqlParameterSource("limit", limit),
                (rs, rowNum) -> summary(rs)
        );
    }

    private List<CatalogDtos.HomeStorySummary> searchSummaries(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return summaries("s.published_at DESC", Math.max(1, Math.min(limit, DEFAULT_LIMIT)));
        }
        return jdbc.query(
                STORY_SUMMARY_SELECT.formatted(PUBLISHED_STORY_FILTER + """
                        AND (s.title LIKE :query OR s.short_description LIKE :query OR s.description LIKE :query)
                        ORDER BY s.published_at DESC
                        LIMIT :limit
                        """),
                new MapSqlParameterSource()
                        .addValue("query", "%" + q + "%")
                        .addValue("limit", Math.max(1, Math.min(limit, DEFAULT_LIMIT))),
                (rs, rowNum) -> summary(rs)
        );
    }

    private CatalogDtos.RankingBoard rankingBoard(String id, String title, String subtitle, String unit, String rankingType) {
        List<CatalogDtos.RankingStory> stories = jdbc.query(
                """
                        SELECT r.`rank`, r.score, s.id, s.team_id, t.name AS team_name, s.slug, s.title,
                               s.cover_url, s.published_at, s.view_count_cache, s.favorite_count_cache
                        FROM ranking_snapshots r
                        JOIN stories s ON s.id = r.story_id
                        JOIN teams t ON t.id = s.team_id
                        WHERE r.ranking_type = :rankingType
                          AND r.period = 'DAILY'
                          AND r.snapshot_date = (SELECT MAX(snapshot_date) FROM ranking_snapshots WHERE ranking_type = :rankingType)
                          AND %s
                        ORDER BY r.`rank` ASC
                        LIMIT 10
                        """.formatted(PUBLISHED_STORY_FILTER),
                Map.of("rankingType", rankingType),
                (rs, rowNum) -> new CatalogDtos.RankingStory(
                        rs.getInt("rank"),
                        rs.getLong("score"),
                        summary(rs)
                )
        );
        return new CatalogDtos.RankingBoard(id, title, subtitle, unit, stories);
    }

    private List<String> categoryIds(String storyId) {
        return jdbc.queryForList(
                "SELECT genre_id FROM story_genres WHERE story_id = :storyId ORDER BY genre_id",
                Map.of("storyId", storyId),
                String.class
        );
    }

    private String resolveStoryId(String identifier) {
        return jdbc.queryForList(
                        """
                                SELECT id
                                FROM stories
                                WHERE (id = :identifier OR slug = :identifier)
                                  AND status = 'PUBLISHED'
                                  AND published_at IS NOT NULL
                                LIMIT 1
                                """,
                        Map.of("identifier", identifier),
                        String.class
                ).stream()
                .findFirst()
                .orElseThrow(() -> notFound("Story not found"));
    }

    private CatalogDtos.ChapterLink adjacentChapter(String storyId, java.math.BigDecimal number, String operator) {
        String order = "<".equals(operator) ? "DESC" : "ASC";
        return jdbc.query(
                        """
                                SELECT id, chapter_number, slug, title
                                FROM chapters
                                WHERE story_id = :storyId
                                  AND chapter_number %s :number
                                  AND status = 'PUBLISHED'
                                  AND published_at IS NOT NULL
                                ORDER BY chapter_number %s
                                LIMIT 1
                                """.formatted(operator, order),
                        new MapSqlParameterSource()
                                .addValue("storyId", storyId)
                                .addValue("number", number),
                        (rs, rowNum) -> new CatalogDtos.ChapterLink(
                                rs.getString("id"),
                                rs.getBigDecimal("chapter_number"),
                                rs.getString("slug"),
                                rs.getString("title")
                        )
                ).stream()
                .findFirst()
                .orElse(null);
    }

    private CatalogDtos.HomeStorySummary summary(ResultSet rs, int rowNum) throws SQLException {
        return summary(rs);
    }

    private CatalogDtos.HomeStorySummary summary(ResultSet rs) throws SQLException {
        return new CatalogDtos.HomeStorySummary(
                rs.getString("id"),
                rs.getString("team_id"),
                rs.getString("team_name"),
                rs.getString("slug"),
                rs.getString("title"),
                rs.getString("cover_url"),
                instantString(rs, "published_at"),
                rs.getLong("view_count_cache"),
                rs.getLong("favorite_count_cache")
        );
    }

    private CatalogDtos.PublicChapter chapter(ResultSet rs) throws SQLException {
        return new CatalogDtos.PublicChapter(
                rs.getString("id"),
                rs.getString("story_id"),
                rs.getBigDecimal("chapter_number"),
                rs.getString("slug"),
                rs.getString("title"),
                instantString(rs, "published_at"),
                1
        );
    }

    private String contentHtml(String content) {
        String text = content == null || content.isBlank() ? "Nội dung chương đang được cập nhật." : content.trim();
        Document document = Jsoup.parseBodyFragment("");
        Element body = document.body();
        for (String paragraph : text.split("\\R+")) {
            body.appendElement("p").text(paragraph.trim());
        }
        return Jsoup.clean(body.html(), Safelist.basic());
    }

    private int wordCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.trim().split("\\s+").length;
    }

    private String mapCompletionStatus(String status) {
        return "PAUSED".equals(status) ? "HIATUS" : status;
    }

    private String firstText(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }

    private String instantString(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant().toString();
    }

    private ApiException notFound(String title) {
        return new ApiException(HttpStatus.NOT_FOUND, "catalog.not_found", title, title);
    }
}
