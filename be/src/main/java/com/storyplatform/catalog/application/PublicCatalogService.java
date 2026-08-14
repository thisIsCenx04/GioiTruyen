package com.storyplatform.catalog.application;

import com.storyplatform.catalog.application.dto.CatalogDtos;
import com.storyplatform.shared.api.ApiException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
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
                   s.story_format, s.story_type, s.published_at, s.view_count_cache,
                   s.favorite_count_cache, s.original_author
            FROM stories s
            JOIN teams t ON t.id = s.team_id
            WHERE %s
            """;
    /** Only Zhihu-style one-page stories. */
    private static final String ONESHOT_FILTER = "s.story_format = 'ONESHOT'";
    /** Everything except one-page stories, so the main catalog stays serial-only. */
    private static final String SERIAL_FILTER = "s.story_format <> 'ONESHOT'";
    /** Most chapters one page may carry; the story itself is unbounded. */
    private static final int MAX_CHAPTER_PAGE_SIZE = 100;
    /** Cards per shelf on the catalog pages. */
    private static final int SHELF_SIZE = 8;

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
                                summaries("s.published_at DESC", 8,
                                        SERIAL_FILTER + " AND s.progress_status = 'COMPLETED'")
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
                               s.story_format, s.published_at, s.view_count_cache, s.favorite_count_cache,
                               s.original_author
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

    /**
     * Shelves for the catalog page, in the order they are read: exclusives lead,
     * then editorial picks, then what changed recently, then original writing.
     * Empty shelves are dropped so the page never shows a heading over nothing.
     */
    public List<CatalogDtos.TaggedStorySection> storySections() {
        List<CatalogDtos.TaggedStorySection> sections = List.of(
                new CatalogDtos.TaggedStorySection(
                        "exclusive",
                        "EXCLUSIVE",
                        "Truyện độc quyền",
                        summaries("s.published_at DESC", SHELF_SIZE,
                                SERIAL_FILTER + " AND s.story_type = 'EXCLUSIVE'")
                ),
                new CatalogDtos.TaggedStorySection(
                        "recommended",
                        "RECOMMENDED",
                        "Truyện đề cử",
                        summaries("s.recommendation_gem_cache DESC, s.view_count_cache DESC", SHELF_SIZE)
                ),
                new CatalogDtos.TaggedStorySection(
                        "recent-update",
                        "RECENT_UPDATE",
                        "Truyện vừa cập nhật",
                        summaries("s.last_chapter_at DESC, s.updated_at DESC", SHELF_SIZE)
                ),
                new CatalogDtos.TaggedStorySection(
                        "original",
                        "ORIGINAL",
                        "Truyện sáng tác",
                        summaries("s.published_at DESC", SHELF_SIZE,
                                SERIAL_FILTER + " AND s.story_type = 'ORIGINAL'")
                ),
                new CatalogDtos.TaggedStorySection(
                        "audio",
                        "AUDIO",
                        "Truyện audio",
                        summaries("s.published_at DESC", SHELF_SIZE,
                                SERIAL_FILTER + " AND s.story_type = 'AUDIO'")
                ),
                new CatalogDtos.TaggedStorySection(
                        "completed",
                        "COMPLETED",
                        "Đã hoàn thành",
                        summaries("s.published_at DESC", SHELF_SIZE,
                                SERIAL_FILTER + " AND s.progress_status = 'COMPLETED'")
                )
        );
        return sections.stream().filter(section -> !section.stories().isEmpty()).toList();
    }

    /** Stories carrying a given tag, for the tag links shown on cards and detail pages. */
    public List<CatalogDtos.HomeStorySummary> tagStories(String slug) {
        return jdbc.query(
                // Leading newline required; see searchSummaries for the failure mode.
                STORY_SUMMARY_SELECT.formatted(PUBLISHED_STORY_FILTER + """

                        AND EXISTS (
                            SELECT 1 FROM story_tags st
                            WHERE st.story_id = s.id AND st.slug = :slug
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

    public List<CatalogDtos.RankingBoard> rankingBoards() {
        return List.of(
                rankingBoard("gold", "Bảng doanh thu", "Truyện có doanh thu xu cao", "xu", "COIN_REVENUE"),
                rankingBoard("recommendations", "Bảng đề cử", "Truyện được tặng ngọc nhiều", "ngọc", "GEM_RECOMMENDATION"),
                rankingBoard("views", "Bảng lượt xem", "Truyện được đọc nhiều", "lượt", "VIEWS")
        );
    }

    /** Shelves for the Zhihu page: one-page stories only. */
    public List<CatalogDtos.TaggedStorySection> zhihuSections() {
        return List.of(
                new CatalogDtos.TaggedStorySection(
                        "zhihu-new",
                        "NEW_RELEASE",
                        "Truyện ngắn mới đăng",
                        summaries("s.published_at DESC", 24, ONESHOT_FILTER)
                ),
                new CatalogDtos.TaggedStorySection(
                        "zhihu-popular",
                        "POPULAR",
                        "Được đọc nhiều",
                        summaries("s.view_count_cache DESC, s.published_at DESC", 12, ONESHOT_FILTER)
                ),
                new CatalogDtos.TaggedStorySection(
                        "zhihu-loved",
                        "MOST_LOVED",
                        "Nhiều lượt thích nhất",
                        summaries("s.favorite_count_cache DESC, s.published_at DESC", 12, ONESHOT_FILTER)
                )
        );
    }

    /** Ranking boards scoped to one-page stories, so shorts never sit beside novels. */
    public List<CatalogDtos.RankingBoard> zhihuRankingBoards() {
        return List.of(
                rankingBoard("zhihu-views", "Bảng lượt đọc", "Truyện ngắn được đọc nhiều", "lượt",
                        "VIEWS", ONESHOT_FILTER),
                rankingBoard("zhihu-recommendations", "Bảng đề cử", "Truyện ngắn được tặng ngọc nhiều", "ngọc",
                        "GEM_RECOMMENDATION", ONESHOT_FILTER),
                rankingBoard("zhihu-gold", "Bảng doanh thu", "Truyện ngắn có doanh thu xu cao", "xu",
                        "COIN_REVENUE", ONESHOT_FILTER)
        );
    }

    public List<CatalogDtos.HomeStorySummary> categoryStories(String slug) {
        return jdbc.query(
                // Leading newline required; see searchSummaries for the failure mode.
                STORY_SUMMARY_SELECT.formatted(PUBLISHED_STORY_FILTER + " AND " + SERIAL_FILTER + """

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
                                       cover_url, story_format, story_type, progress_status,
                                       published_at, updated_at
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
                                rs.getString("cover_url"),
                                categoryIds(rs.getString("id")),
                                rs.getString("original_title") == null ? "ORIGINAL" : "TRANSLATED",
                                "vi-VN",
                                mapCompletionStatus(rs.getString("progress_status")),
                                storyFormat(rs),
                                storyType(rs),
                                storyTags(rs.getString("id")),
                                instantString(rs, "published_at"),
                                instantString(rs, "updated_at"),
                                1
                        )
                ).stream()
                .findFirst()
                .orElseThrow(() -> notFound("Story not found"));
    }

    /**
     * One page of a story's chapters.
     *
     * <p>Paged by offset rather than a cursor because the reader needs to jump:
     * a thousand-chapter story is fifty pages, and someone returning to it wants
     * the last page, not fifty presses of "next". That requires a total, which a
     * cursor cannot give.
     *
     * <p>The page size is capped, not the story: an earlier cap of 100 applied
     * to the whole list, so chapter 101 onwards could neither be listed nor
     * opened - the reader page resolves a chapter from this same list.
     */
    public CatalogDtos.ChapterPage chapters(String identifier, int page, int size, String readerId) {
        String storyId = resolveStoryId(identifier);
        int safeSize = Math.max(1, Math.min(size, MAX_CHAPTER_PAGE_SIZE));
        int safePage = Math.max(1, page);

        long total = jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM chapters
                        WHERE story_id = :storyId
                          AND status = 'PUBLISHED'
                          AND published_at IS NOT NULL
                        """,
                new MapSqlParameterSource("storyId", storyId),
                Long.class
        );
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        List<CatalogDtos.PublicChapter> items = jdbc.query(
                """
                        SELECT c.id, c.story_id, c.chapter_number, c.slug, c.title, c.published_at,
                               c.access_type, c.coin_price,
                               (u.chapter_id IS NOT NULL) AS is_unlocked
                        FROM chapters c
                        LEFT JOIN chapter_unlocks u
                               ON u.chapter_id = c.id AND u.user_id = :readerId
                        WHERE c.story_id = :storyId
                          AND c.status = 'PUBLISHED'
                          AND c.published_at IS NOT NULL
                        ORDER BY c.chapter_number ASC
                        LIMIT :limit OFFSET :offset
                        """,
                new MapSqlParameterSource()
                        .addValue("storyId", storyId)
                        // A guest matches no unlock row, so every paid chapter
                        // shows as locked rather than failing the join.
                        .addValue("readerId", readerId == null ? "" : readerId)
                        .addValue("limit", safeSize)
                        .addValue("offset", (long) (safePage - 1) * safeSize),
                (rs, rowNum) -> chapter(rs)
        );
        return new CatalogDtos.ChapterPage(items, safePage, safeSize, total, totalPages);
    }

    /**
     * A chapter as the reader is allowed to see it.
     *
     * <p>A paid chapter that this reader has not bought comes back with its
     * metadata but no text at all. Filtering the content in the browser was
     * never enough: the API returned the whole chapter, so anyone could read it
     * by opening the URL directly or looking at the network response.
     */
    /**
     * The chapter a reader asked for by number, e.g. "chuong-12" of a story.
     *
     * <p>Resolved here rather than by scanning the chapter list, which only ever
     * held one page of it.
     */
    public CatalogDtos.PublishedChapterDetail chapterByNumber(
            String identifier, String number, String readerId) {
        String storyId = resolveStoryId(identifier);
        java.math.BigDecimal chapterNumber;
        try {
            chapterNumber = new java.math.BigDecimal(number.trim());
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "chapter.not_found",
                    "Chapter not found", "Không tìm thấy chương này.");
        }
        String chapterId = jdbc.query(
                        """
                                SELECT id FROM chapters
                                WHERE story_id = :storyId
                                  AND chapter_number = :number
                                  AND status = 'PUBLISHED'
                                  AND published_at IS NOT NULL
                                LIMIT 1
                                """,
                        new MapSqlParameterSource()
                                .addValue("storyId", storyId)
                                .addValue("number", chapterNumber),
                        (rs, rowNum) -> rs.getString("id"))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "chapter.not_found",
                        "Chapter not found", "Không tìm thấy chương này."));
        return chapter(chapterId, readerId);
    }

    public CatalogDtos.PublishedChapterDetail chapter(String chapterId, String readerId) {
        return jdbc.query(
                        """
                                SELECT c.id, c.story_id, c.chapter_number, c.slug, c.title, c.content,
                                       c.access_type, c.coin_price, c.published_at, c.updated_at,
                                       (u.chapter_id IS NOT NULL) AS is_unlocked
                                FROM chapters c
                                LEFT JOIN chapter_unlocks u
                                       ON u.chapter_id = c.id AND u.user_id = :readerId
                                WHERE c.id = :chapterId
                                  AND c.status = 'PUBLISHED'
                                  AND c.published_at IS NOT NULL
                                LIMIT 1
                                """,
                        Map.of("chapterId", chapterId, "readerId", readerId == null ? "" : readerId),
                        (rs, rowNum) -> {
                            String content = rs.getString("content");
                            boolean paid = "PAID".equalsIgnoreCase(rs.getString("access_type"));
                            long coinPrice = rs.getLong("coin_price");
                            boolean unlocked = !paid || rs.getBoolean("is_unlocked");
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
                                    // The text itself is withheld, not merely hidden.
                                    unlocked ? contentHtml(content) : "",
                                    unlocked ? wordCount(content) : 0,
                                    instantString(rs, "updated_at"),
                                    adjacentChapter(rs.getString("story_id"), rs.getBigDecimal("chapter_number"), "<"),
                                    adjacentChapter(rs.getString("story_id"), rs.getBigDecimal("chapter_number"), ">"),
                                    paid ? "PAID" : "FREE",
                                    coinPrice,
                                    unlocked
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

    /**
     * The main catalog lists serialised stories only; Zhihu one-shots have their
     * own surfaces so a short story never competes for a shelf slot meant for a
     * novel. Pass an explicit filter to opt into the other format.
     */
    /**
     * The stories a reader kept, newest first.
     *
     * <p>Shares the summary shape with every shelf, so a saved story renders
     * with the same card the reader saved it from.
     */
    public List<CatalogDtos.HomeStorySummary> library(String userId) {
        return jdbc.query(
                """
                        SELECT s.id, s.team_id, t.name AS team_name, s.slug, s.title, s.cover_url,
                               s.story_format, s.story_type, s.published_at, s.view_count_cache,
                               s.favorite_count_cache, s.original_author
                        FROM library_items l
                        JOIN stories s ON s.id = l.story_id
                        JOIN teams t ON t.id = s.team_id
                        WHERE l.user_id = :userId AND s.status = 'PUBLISHED'
                        ORDER BY l.created_at DESC
                        """,
                new MapSqlParameterSource("userId", userId),
                (rs, rowNum) -> summary(rs)
        );
    }

    private List<CatalogDtos.HomeStorySummary> summaries(String orderBy, int limit) {
        return summaries(orderBy, limit, SERIAL_FILTER);
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
                // The leading newline matters: without it the filter's trailing
                // "NULL" fuses with "AND" into "NULLAND" and MySQL rejects the query.
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
        return rankingBoard(id, title, subtitle, unit, rankingType, SERIAL_FILTER);
    }

    private CatalogDtos.RankingBoard rankingBoard(
            String id, String title, String subtitle, String unit, String rankingType, String formatFilter) {
        List<CatalogDtos.RankingStory> stories = jdbc.query(
                """
                        SELECT r.`rank`, r.score, s.id, s.team_id, t.name AS team_name, s.slug, s.title,
                               s.cover_url, s.story_format, s.published_at, s.view_count_cache,
                               s.favorite_count_cache, s.original_author
                        FROM ranking_snapshots r
                        JOIN stories s ON s.id = r.story_id
                        JOIN teams t ON t.id = s.team_id
                        WHERE r.ranking_type = :rankingType
                          AND r.period = 'DAILY'
                          AND r.snapshot_date = (SELECT MAX(snapshot_date) FROM ranking_snapshots WHERE ranking_type = :rankingType)
                          AND %s
                          AND %s
                        ORDER BY r.`rank` ASC
                        LIMIT 10
                        """.formatted(PUBLISHED_STORY_FILTER, formatFilter),
                Map.of("rankingType", rankingType),
                (rs, rowNum) -> new CatalogDtos.RankingStory(
                        rs.getInt("rank"),
                        // The gem total behind a recommendation ranking stays
                        // private: the order is public, the amount spent is
                        // not, or the board becomes a spending leaderboard.
                        "GEM_RECOMMENDATION".equals(rankingType) ? 0L : rs.getLong("score"),
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

    private List<CatalogDtos.StoryTag> storyTags(String storyId) {
        return jdbc.query(
                "SELECT slug, label FROM story_tags WHERE story_id = :storyId ORDER BY label",
                Map.of("storyId", storyId),
                (rs, rowNum) -> new CatalogDtos.StoryTag(rs.getString("slug"), rs.getString("label"))
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
                rs.getLong("favorite_count_cache"),
                storyFormat(rs),
                storyType(rs),
                hasColumn(rs, "original_author") ? rs.getString("original_author") : null
        );
    }

    /** Rows selected before the format column existed default to SERIAL. */
    private static String storyFormat(ResultSet rs) throws SQLException {
        String format = hasColumn(rs, "story_format") ? rs.getString("story_format") : null;
        return format == null || format.isBlank() ? "SERIAL" : format;
    }

    /** Everything is plain text unless the admin classified it otherwise. */
    private static String storyType(ResultSet rs) throws SQLException {
        String type = hasColumn(rs, "story_type") ? rs.getString("story_type") : null;
        return type == null || type.isBlank() ? "TEXT" : type;
    }

    private static boolean hasColumn(ResultSet rs, String column) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            if (column.equalsIgnoreCase(metaData.getColumnLabel(index))) {
                return true;
            }
        }
        return false;
    }

    private CatalogDtos.PublicChapter chapter(ResultSet rs) throws SQLException {
        boolean paid = "PAID".equalsIgnoreCase(rs.getString("access_type"));
        return new CatalogDtos.PublicChapter(
                rs.getString("id"),
                rs.getString("story_id"),
                rs.getBigDecimal("chapter_number"),
                rs.getString("slug"),
                rs.getString("title"),
                instantString(rs, "published_at"),
                1,
                paid ? "PAID" : "FREE",
                rs.getLong("coin_price"),
                !paid || rs.getBoolean("is_unlocked")
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
