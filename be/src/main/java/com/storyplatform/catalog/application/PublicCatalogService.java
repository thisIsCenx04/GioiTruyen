package com.storyplatform.catalog.application;

import com.storyplatform.catalog.application.dto.CatalogDtos;
import com.storyplatform.shared.api.ApiException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
                   s.favorite_count_cache, s.original_author, s.progress_status,
                   -- How far the story has got, for the "Chương N" line on a card.
                   COALESCE((SELECT MAX(c.chapter_number) FROM chapters c
                              WHERE c.story_id = s.id AND c.status = 'PUBLISHED'), 0)
                       AS latest_chapter_number
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
    /**
     * Cards per shelf on the catalog pages.
     *
     * <p>Twelve fills two full rows at every breakpoint the grid uses (six, five,
     * four, three and two per row). At eight the home page drew one full row and
     * a ragged second one.
     */
    private static final int SHELF_SIZE = 12;
    /** Ranking window covering everything, as opposed to a number of days. */
    private static final String PERIOD_ALL = "ALL";

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
                        SELECT g.id, g.slug, g.name,
                               (SELECT COUNT(*)
                                  FROM story_genres sg
                                  JOIN stories s ON s.id = sg.story_id
                                 WHERE sg.genre_id = g.id
                                   AND s.status = 'PUBLISHED'
                                   AND s.published_at IS NOT NULL) AS story_count
                        FROM genres g
                        WHERE g.is_active = TRUE
                        ORDER BY g.name ASC
                        """,
                Map.of(),
                (rs, rowNum) -> new CatalogDtos.CategoryItem(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getLong("story_count")
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
                               -- Type and progress carry the ĐỘC QUYỀN and FULL
                               -- marks; without them a promoted card lost badges
                               -- the same story shows everywhere else.
                               s.original_author, s.story_type, s.progress_status
                        FROM story_promotions p
                        JOIN stories s ON s.id = p.story_id
                        JOIN teams t ON t.id = s.team_id
                        WHERE p.status = 'ACTIVE'
                          AND p.starts_at <= NOW(3)
                          AND p.ends_at > NOW(3)
                          AND %s
                        -- slot_position is what the admin board arranges, so it
                        -- decides the order here; ordering by created_at made
                        -- the drag-and-drop board purely decorative. Bookings
                        -- with no slot yet fall to the end in arrival order.
                        ORDER BY p.slot_position IS NULL, p.slot_position ASC, p.created_at ASC
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
                        // No serial filter: "audio" is a story type the publisher
                        // picks, not a length. A Zhihu story marked audio was
                        // being kept off its own shelf.
                        summaries("s.published_at DESC", SHELF_SIZE,
                                "s.story_type = 'AUDIO'")
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

    /**
     * Everything a team has published, for its public profile.
     *
     * <p>The profile used to fetch the home shelves and keep the entries whose
     * team matched. Those shelves hold eight stories each, so a team whose work
     * had scrolled off them showed an empty list under a heading that correctly
     * said it had published two - the page contradicted itself. Asking the
     * database directly is the only way the list can be complete.
     *
     * <p>Accepts an id or a slug because the profile URL carries either.
     */
    public List<CatalogDtos.HomeStorySummary> teamStories(String teamIdOrSlug) {
        return jdbc.query(
                // Leading newline required; see searchSummaries for the failure mode.
                STORY_SUMMARY_SELECT.formatted(PUBLISHED_STORY_FILTER + """

                        AND (t.id = :team OR t.slug = :team)
                        ORDER BY s.last_chapter_at DESC, s.published_at DESC
                        """),
                new MapSqlParameterSource("team", teamIdOrSlug),
                (rs, rowNum) -> summary(rs)
        );
    }

    /**
     * The three public boards, over the requested window.
     *
     * <p>Only the view board publishes its number. Coin revenue and gem
     * recommendations decide the order and nothing else: showing either total
     * would turn the page into a spending leaderboard and expose what a story
     * earns.
     */
    public List<CatalogDtos.RankingBoard> rankingBoards(String period) {
        String window = rankingWindow(period);
        return List.of(
                rankingBoard("gold", "Thánh bảng", "REVENUE", window, SERIAL_FILTER),
                rankingBoard("views", "Top lượt xem", "VIEWS", window, SERIAL_FILTER),
                rankingBoard("recommendations", "Bảng đề cử", "GEM_RECOMMENDATION", window, SERIAL_FILTER)
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
        String window = rankingWindow(PERIOD_ALL);
        return List.of(
                rankingBoard("zhihu-views", "Top lượt xem", "VIEWS", window, ONESHOT_FILTER),
                rankingBoard("zhihu-recommendations", "Bảng đề cử", "GEM_RECOMMENDATION", window, ONESHOT_FILTER),
                rankingBoard("zhihu-favorites", "Bảng yêu thích", "FAVORITES", window, ONESHOT_FILTER)
        );
    }

    /**
     * Every published story carrying a genre, Zhihu stories included.
     *
     * <p>The serial filter used to apply here, which hid one-shots from genre
     * browsing entirely: an admin could tag a Zhihu story "Đô Thị" and it would
     * appear nowhere under that genre. One-shots are read chapter by chapter
     * like anything else now, so there is nothing left for the filter to
     * protect - and it made the tab's count disagree with the tab's contents.
     */
    public List<CatalogDtos.HomeStorySummary> categoryStories(String slug) {
        return jdbc.query(
                // Leading newline required; see searchSummaries for the failure mode.
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
    private boolean checkIsAdmin(String readerId, boolean jwtIsAdmin) {
        if (jwtIsAdmin) {
            return true;
        }
        if (readerId == null || readerId.isBlank()) {
            return false;
        }
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE id = :id AND role = 'ADMIN'",
                    Map.of("id", readerId),
                    Integer.class
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Whether this reader sees a paid chapter without paying for it.
     *
     * <p>True for an admin, and for anyone on the team that published the story.
     * A team cannot earn from its own catalogue - buying your own chapter moves
     * coins out of your wallet and most of them back into your team's ledger,
     * which is not revenue, just noise in the books. Locking authors out of the
     * work they wrote to avoid that was the worse trade: they could not proof
     * their own paid chapters as a reader sees them.
     *
     * <p>Membership is not filtered by role on purpose. Every role on a team can
     * already read the chapter through the publishing workspace; the paywall
     * would only be making them take a longer route to the same text.
     */
    private boolean canBypassPaywall(String storyId, String readerId, boolean jwtIsAdmin) {
        if (checkIsAdmin(readerId, jwtIsAdmin)) {
            return true;
        }
        if (readerId == null || readerId.isBlank() || storyId == null || storyId.isBlank()) {
            return false;
        }
        try {
            Integer count = jdbc.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM stories s
                            JOIN team_members tm ON tm.team_id = s.team_id
                            WHERE s.id = :storyId
                              AND tm.user_id = :readerId
                              AND tm.status = 'ACTIVE'
                            """,
                    Map.of("storyId", storyId, "readerId", readerId),
                    Integer.class
            );
            return count != null && count > 0;
        } catch (Exception exception) {
            // A paywall that fails open would give the text away, so a lookup
            // that cannot run leaves the chapter locked.
            return false;
        }
    }

    public CatalogDtos.ChapterPage chapters(String identifier, int page, int size, String readerId) {
        return chapters(identifier, page, size, readerId, false);
    }

    public CatalogDtos.ChapterPage chapters(String identifier, int page, int size, String readerId, boolean jwtIsAdmin) {
        String storyId = resolveStoryId(identifier);
        boolean isAdmin = canBypassPaywall(storyId, readerId, jwtIsAdmin);
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
                               (u.chapter_id IS NOT NULL OR combo.story_id IS NOT NULL OR :isAdmin = true) AS is_unlocked
                        FROM chapters c
                        LEFT JOIN chapter_unlocks u
                               ON u.chapter_id = c.id AND u.user_id = :readerId
                        LEFT JOIN story_combo_purchases combo
                               ON combo.story_id = c.story_id AND combo.user_id = :readerId
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
                        .addValue("isAdmin", isAdmin)
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
        return chapterByNumber(identifier, number, readerId, false);
    }

    public CatalogDtos.PublishedChapterDetail chapterByNumber(
            String identifier, String number, String readerId, boolean jwtIsAdmin) {
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
        return chapter(chapterId, readerId, jwtIsAdmin);
    }

    public CatalogDtos.PublishedChapterDetail chapter(String chapterId, String readerId) {
        return chapter(chapterId, readerId, false);
    }

    public CatalogDtos.PublishedChapterDetail chapter(String chapterId, String readerId, boolean jwtIsAdmin) {
        // The owning story decides whether this reader is one of its authors, so
        // it is looked up before the chapter rather than read off the row - the
        // unlock flag is part of the same query the row comes from.
        String owningStoryId = jdbc.query(
                        "SELECT story_id FROM chapters WHERE id = :chapterId LIMIT 1",
                        Map.of("chapterId", chapterId),
                        (rs, rowNum) -> rs.getString("story_id"))
                .stream()
                .findFirst()
                .orElse(null);
        boolean isAdmin = canBypassPaywall(owningStoryId, readerId, jwtIsAdmin);
        return jdbc.query(
                        """
                                SELECT c.id, c.story_id, c.chapter_number, c.slug, c.title, c.content,
                                       c.access_type, c.coin_price, c.published_at, c.updated_at,
                                       (u.chapter_id IS NOT NULL OR combo.story_id IS NOT NULL OR :isAdmin = true) AS is_unlocked
                                FROM chapters c
                                LEFT JOIN chapter_unlocks u
                                       ON u.chapter_id = c.id AND u.user_id = :readerId
                                LEFT JOIN story_combo_purchases combo
                                       ON combo.story_id = c.story_id AND combo.user_id = :readerId
                                WHERE c.id = :chapterId
                                  AND c.status = 'PUBLISHED'
                                  AND c.published_at IS NOT NULL
                                LIMIT 1
                                """,
                        Map.of(
                                "chapterId", chapterId,
                                "readerId", readerId == null ? "" : readerId,
                                "isAdmin", isAdmin
                        ),
                        (rs, rowNum) -> {
                            String content = rs.getString("content");
                            boolean paid = "PAID".equalsIgnoreCase(rs.getString("access_type"));
                            long coinPrice = rs.getLong("coin_price");
                            boolean unlocked = !paid || rs.getBoolean("is_unlocked") || isAdmin;
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

    /**
     * Type-ahead suggestions, matched on the title alone.
     *
     * <p>Suggestions used to run the full search, which also looks inside the
     * synopsis - so typing "ma" offered stories whose blurb happened to contain
     * the word and whose title said nothing of the sort. In a dropdown of eight
     * rows that is worse than useless: the reader is picking by title, so the
     * title is what has to match.
     *
     * <p>Ordered by how well it matches rather than by date. A title that starts
     * with what was typed comes first, then one where the word starts inside the
     * title, then anything merely containing it; ties break on views, so of two
     * equally good matches the one readers actually read leads. Sorting by
     * published_at put the newest near-miss above the obvious answer.
     */
    public CatalogDtos.SuggestionResponse suggestions(String query, int limit) {
        String q = query == null ? "" : query.trim();
        int safeLimit = Math.max(1, Math.min(limit, DEFAULT_LIMIT));
        if (q.isEmpty()) {
            return new CatalogDtos.SuggestionResponse(List.of(), null, false);
        }

        List<CatalogDtos.SuggestionItem> items = jdbc.query(
                """
                        SELECT s.id, s.slug, s.title, s.cover_url
                        FROM stories s
                        WHERE s.status = 'PUBLISHED'
                          AND s.published_at IS NOT NULL
                          AND s.title LIKE :contains
                        ORDER BY
                            CASE
                                WHEN s.title LIKE :prefix THEN 0
                                WHEN s.title LIKE :wordStart THEN 1
                                ELSE 2
                            END,
                            s.view_count_cache DESC,
                            CHAR_LENGTH(s.title),
                            s.published_at DESC
                        LIMIT :limit
                        """,
                new MapSqlParameterSource()
                        .addValue("contains", "%" + q + "%")
                        .addValue("prefix", q + "%")
                        // A space in front means the match begins a word rather
                        // than landing in the middle of one.
                        .addValue("wordStart", "% " + q + "%")
                        .addValue("limit", safeLimit),
                (rs, rowNum) -> new CatalogDtos.SuggestionItem(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("title"),
                        rs.getString("cover_url"))
        );
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
        return library(userId, "favorites");
    }

    /**
     * Một kệ trong tủ truyện của người đọc.
     *
     * <p>Bốn kệ, bốn nguồn dữ liệu khác nhau. Trước đây chỉ có một truy vấn duy
     * nhất và ba cái tab ở giao diện chỉ đổi màu nút - "Đang đọc" và "Lịch sử
     * đọc" hiện đúng cùng một danh sách với "Yêu thích", nên hai tab đó không
     * nói lên điều gì.
     *
     * @param shelf favorites | reading | history | combo
     */
    public List<CatalogDtos.HomeStorySummary> library(String userId, String shelf) {
        String columns = """
                SELECT s.id, s.team_id, t.name AS team_name, s.slug, s.title, s.cover_url,
                       s.story_format, s.story_type, s.published_at, s.view_count_cache,
                       s.favorite_count_cache, s.original_author, s.progress_status
                """;
        String sql = switch (shelf == null ? "" : shelf) {
            // Đang đọc: có tiến độ đọc và truyện chưa hoàn thành, tức là còn
            // chương để đọc tiếp. Xếp theo lần đọc gần nhất.
            case "reading" -> columns + """
                    FROM reading_progress r
                    JOIN stories s ON s.id = r.story_id
                    JOIN teams t ON t.id = s.team_id
                    WHERE r.user_id = :userId AND s.status = 'PUBLISHED'
                      AND s.progress_status <> 'COMPLETED'
                    ORDER BY r.updated_at DESC
                    LIMIT 200
                    """;
            // Lịch sử: mọi truyện từng đọc, kể cả đã đọc xong.
            case "history" -> columns + """
                    FROM reading_progress r
                    JOIN stories s ON s.id = r.story_id
                    JOIN teams t ON t.id = s.team_id
                    WHERE r.user_id = :userId AND s.status = 'PUBLISHED'
                    ORDER BY r.updated_at DESC
                    LIMIT 200
                    """;
            // Đã mua trọn bộ. GROUP BY vì một truyện có thể mua nhiều lần nếu
            // tác giả mở bán lại phần sau - kệ chỉ cần mỗi truyện một lần.
            case "combo" -> columns + """
                    FROM story_combo_purchases c
                    JOIN stories s ON s.id = c.story_id
                    JOIN teams t ON t.id = s.team_id
                    WHERE c.user_id = :userId AND s.status = 'PUBLISHED'
                    GROUP BY s.id, s.team_id, t.name, s.slug, s.title, s.cover_url,
                             s.story_format, s.story_type, s.published_at, s.view_count_cache,
                             s.favorite_count_cache, s.original_author, s.progress_status
                    ORDER BY MAX(c.created_at) DESC
                    LIMIT 200
                    """;
            default -> columns + """
                    FROM library_items l
                    JOIN stories s ON s.id = l.story_id
                    JOIN teams t ON t.id = s.team_id
                    WHERE l.user_id = :userId AND s.status = 'PUBLISHED'
                    ORDER BY l.created_at DESC
                    LIMIT 200
                    """;
        };
        return jdbc.query(sql, new MapSqlParameterSource("userId", userId), (rs, rowNum) -> summary(rs));
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

    /**
     * Turns a requested period into the SQL condition that scopes a board.
     *
     * <p>"All time" reads the running totals on the story row; a bounded window
     * has to be summed from the daily rollups instead.
     */
    private static String rankingWindow(String period) {
        String requested = period == null ? PERIOD_ALL : period.trim().toUpperCase(Locale.ROOT);
        return switch (requested) {
            case "WEEK" -> "7";
            case "MONTH" -> "30";
            default -> PERIOD_ALL;
        };
    }

    /**
     * Boards are ranked from live data rather than from {@code ranking_snapshots}.
     * Nothing in the application writes a snapshot, so the boards only ever showed
     * the seeded rows: a gem recommendation moved {@code recommendation_gem_cache}
     * but the board stayed frozen. Reading the counters (all time) or the daily
     * rollups (a bounded window) means a gift counts the moment it is spent.
     *
     * @param window either {@link #PERIOD_ALL} or a number of days as a string
     */
    private CatalogDtos.RankingBoard rankingBoard(
            String id, String title, String metric, String window, String formatFilter) {
        boolean allTime = PERIOD_ALL.equals(window);
        String scoreExpression = switch (metric) {
            case "REVENUE" -> revenueScore(window);
            case "GEM_RECOMMENDATION" -> allTime
                    ? "s.recommendation_gem_cache"
                    : dailyStatScore("recommendations", window);
            case "FAVORITES" -> "s.favorite_count_cache";
            default -> allTime ? "s.view_count_cache" : dailyStatScore("views", window);
        };

        List<CatalogDtos.RankingStory> stories = jdbc.query(
                """
                        SELECT (%1$s) AS score, s.id, s.team_id, t.name AS team_name, s.slug, s.title,
                               s.cover_url, s.story_format, s.story_type, s.published_at, s.view_count_cache,
                               s.favorite_count_cache, s.original_author
                        FROM stories s
                        JOIN teams t ON t.id = s.team_id
                        WHERE %2$s
                          AND %3$s
                          AND (%1$s) > 0
                        ORDER BY score DESC, s.view_count_cache DESC, s.published_at DESC
                        LIMIT 10
                        """.formatted(scoreExpression, PUBLISHED_STORY_FILTER, formatFilter),
                new MapSqlParameterSource(),
                (rs, rowNum) -> new CatalogDtos.RankingStory(
                        rowNum + 1,
                        // Views and recommendations are public counters. Coin
                        // revenue still orders the kim bang without exposing
                        // how much a story earned.
                        "REVENUE".equals(metric) ? 0L : rs.getLong("score"),
                        summary(rs)
                )
        );
        // No subtitle: the board titles say what they rank on their own.
        return new CatalogDtos.RankingBoard(
                id,
                title,
                "",
                "GEM_RECOMMENDATION".equals(metric) ? "ngọc" : "VIEWS".equals(metric) ? "lượt" : "",
                stories);
    }

    /** Sum of one daily-rollup column over the last {@code days} days. */
    private static String dailyStatScore(String column, String days) {
        return """
                COALESCE((SELECT SUM(d.%s) FROM story_daily_stats d
                          WHERE d.story_id = s.id AND d.stat_date >= CURDATE() - INTERVAL %s DAY), 0)
                """.formatted(column, days).strip();
    }

    /**
     * Coins a story brought in: paid chapter unlocks, whole-story combos, and
     * donations aimed at it. {@code story_daily_stats.coin_revenue} is never
     * written, so the money is counted from the records that create it.
     *
     * <p>Combos have to be counted separately. Buying one writes a
     * {@code story_combo_purchases} row and no chapter unlocks, so a board built
     * from unlocks alone reported nothing for the largest purchase on offer.
     */
    private static String revenueScore(String window) {
        String unlockWindow = periodClause("u.created_at", window);
        String comboWindow = periodClause("cb.created_at", window);
        String donationWindow = periodClause("dn.created_at", window);
        return """
                COALESCE((SELECT SUM(u.coin_paid) FROM chapter_unlocks u
                          JOIN chapters c ON c.id = u.chapter_id
                          WHERE c.story_id = s.id%s), 0)
                + COALESCE((SELECT SUM(cb.price_xu) FROM story_combo_purchases cb
                            WHERE cb.story_id = s.id%s), 0)
                + COALESCE((SELECT SUM(dn.gross_coin) FROM donations dn
                            WHERE dn.story_id = s.id%s), 0)
                """.formatted(unlockWindow, comboWindow, donationWindow).strip();
    }

    /** Date filter for a bounded window; empty for the all-time board. */
    private static String periodClause(String column, String window) {
        return PERIOD_ALL.equals(window)
                ? ""
                : " AND %s >= CURDATE() - INTERVAL %s DAY".formatted(column, window);
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
                hasColumn(rs, "original_author") ? rs.getString("original_author") : null,
                // Absent from the few queries that select their own columns;
                // those cards fall back to 0 rather than the query failing.
                hasColumn(rs, "latest_chapter_number") ? rs.getInt("latest_chapter_number") : 0,
                progressStatus(rs)
        );
    }

    /** Rows from queries that select their own columns read as still running. */
    private static String progressStatus(ResultSet rs) throws SQLException {
        String status = hasColumn(rs, "progress_status") ? rs.getString("progress_status") : null;
        return status == null || status.isBlank() ? "ONGOING" : status;
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
