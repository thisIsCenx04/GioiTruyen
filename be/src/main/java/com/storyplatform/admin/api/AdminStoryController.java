package com.storyplatform.admin.api;

import com.storyplatform.admin.application.StoryMediaStorage;
import com.storyplatform.admin.application.dto.AdminDtos.AdminChapterRow;
import com.storyplatform.admin.application.dto.AdminDtos.AdminStoryRow;
import com.storyplatform.admin.application.dto.AdminDtos.UpsertStoryRequest;
import com.storyplatform.catalog.domain.Story;
import com.storyplatform.catalog.domain.StoryContentType;
import com.storyplatform.catalog.domain.StoryFormat;
import com.storyplatform.catalog.domain.StoryType;
import com.storyplatform.catalog.domain.StoryProgressStatus;
import com.storyplatform.catalog.domain.StoryStatus;
import com.storyplatform.catalog.infrastructure.StoryRepository;
import com.storyplatform.shared.api.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/admin/content/stories")
public class AdminStoryController {

    /**
     * A story can carry several genres; the admin table shows a single category,
     * so the first one is picked in a subquery. Joining genres directly would
     * duplicate story rows and break only_full_group_by.
     */
    private static final String LIST_SQL = """
            SELECT s.id, s.slug, s.title, s.original_author, s.short_description, s.cover_url,
                   s.story_format, s.story_type, s.status, s.progress_status, s.updated_at, s.team_id,
                   -- A tab cannot appear in a tag label, so it is a safe joiner.
                   (SELECT GROUP_CONCAT(st.label ORDER BY st.label SEPARATOR '\t')
                    FROM story_tags st WHERE st.story_id = s.id) AS tag_labels,
                   t.name AS team_name,
                   (SELECT g.id FROM story_genres sg
                     JOIN genres g ON g.id = sg.genre_id
                     WHERE sg.story_id = s.id ORDER BY g.name LIMIT 1) AS genre_id,
                   (SELECT g.name FROM story_genres sg
                     JOIN genres g ON g.id = sg.genre_id
                     WHERE sg.story_id = s.id ORDER BY g.name LIMIT 1) AS genre_name,
                   -- Every genre, so the drawer can pre-select the full set.
                   (SELECT GROUP_CONCAT(g.id ORDER BY g.name SEPARATOR '\t')
                     FROM story_genres sg JOIN genres g ON g.id = sg.genre_id
                     WHERE sg.story_id = s.id) AS genre_ids,
                   (SELECT GROUP_CONCAT(g.name ORDER BY g.name SEPARATOR '\t')
                     FROM story_genres sg JOIN genres g ON g.id = sg.genre_id
                     WHERE sg.story_id = s.id) AS genre_names
            FROM stories s
            LEFT JOIN teams t ON t.id = s.team_id
            ORDER BY s.updated_at DESC
            """;

    /**
     * Upper bound on the chapters[i] indexes scanned out of a multipart request.
     * Long-running translations legitimately reach four figures, so this is sized
     * well past 1000 rather than at a round number a real story could hit.
     */
    private static final int MAX_CHAPTERS_PER_REQUEST = 3000;

    /** Matches the stories.short_description column width. */
    private static final int SHORT_DESCRIPTION_LIMIT = 500;

    /** Matches stories.title / chapters.title column widths. */
    private static final int TITLE_LIMIT = 255;

    /** Matches the chapters.slug column width. */
    private static final int CHAPTER_SLUG_LIMIT = 280;

    private final StoryRepository storyRepository;
    private final StoryMediaStorage storyMedia;
    private final JdbcClient jdbc;

    public AdminStoryController(
            StoryRepository storyRepository,
            StoryMediaStorage storyMedia,
            JdbcClient jdbc
    ) {
        this.storyRepository = storyRepository;
        this.storyMedia = storyMedia;
        this.jdbc = jdbc;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminStoryRow> list() {
        return jdbc.sql(LIST_SQL)
                .query((rs, rowNum) -> new AdminStoryRow(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("title"),
                        rs.getString("original_author"),
                        rs.getString("team_name"),
                        rs.getString("team_id"),
                        rs.getString("genre_id"),
                        rs.getString("genre_name"),
                        splitTags(rs.getString("genre_ids")),
                        splitTags(rs.getString("genre_names")),
                        rs.getString("cover_url"),
                        rs.getString("short_description"),
                        splitTags(rs.getString("tag_labels")),
                        rs.getString("story_format"),
                        rs.getString("story_type"),
                        rs.getString("status"),
                        rs.getString("progress_status"),
                        instantText(rs.getTimestamp("updated_at"))
                ))
                .list();
    }

    @GetMapping("/{id}/chapters")
    @Transactional(readOnly = true)
    public List<AdminChapterRow> chapters(@PathVariable UUID id) {
        return jdbc.sql("""
                SELECT id, chapter_number, title, slug, content, access_type, coin_price, status
                FROM chapters
                WHERE story_id = ?
                ORDER BY chapter_number ASC
                """)
                .param(id.toString())
                .query((rs, rowNum) -> new AdminChapterRow(
                        rs.getString("id"),
                        rs.getBigDecimal("chapter_number"),
                        rs.getString("title"),
                        rs.getString("slug"),
                        rs.getString("content"),
                        rs.getString("access_type"),
                        rs.getLong("coin_price"),
                        rs.getString("status")
                ))
                .list();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public AdminStoryRow create(@RequestBody UpsertStoryRequest request) {
        return save(null, request);
    }

    /**
     * The admin workspace switches to multipart when a cover image or chapter
     * files are attached; the scalar fields arrive as individual form parts.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public AdminStoryRow createMultipart(MultipartHttpServletRequest request) {
        return saveMultipart(null, request);
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, path = "/{id}")
    @Transactional
    public AdminStoryRow update(@PathVariable UUID id, @RequestBody UpsertStoryRequest request) {
        return save(id, request);
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, path = "/{id}")
    @Transactional
    public AdminStoryRow updateMultipart(@PathVariable UUID id, MultipartHttpServletRequest request) {
        return saveMultipart(id, request);
    }

    private AdminStoryRow saveMultipart(UUID id, MultipartHttpServletRequest request) {
        AdminStoryRow row = save(id, fromForm(request), storyMedia.storeCover(request.getFile("coverImage")));
        replaceChapters(UUID.fromString(row.id()), readChapters(request));
        return row;
    }

    /**
     * Stories are hidden rather than deleted so that purchases, unlocks and view
     * history that reference them stay intact.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public void archive(@PathVariable UUID id) {
        Story story = find(id);
        story.setStatus(StoryStatus.HIDDEN);
        story.setUpdatedAt(Instant.now());
        storyRepository.save(story);
    }

    private AdminStoryRow save(UUID id, UpsertStoryRequest request) {
        return save(id, request, null);
    }

    private AdminStoryRow save(UUID id, UpsertStoryRequest request, String coverUrl) {
        AdminCategoryController.requireText(request.title(), "title");
        AdminCategoryController.requireText(request.teamId(), "teamId");

        UUID teamId = parseUuid(request.teamId(), "teamId");
        requireTeamExists(teamId);

        String slug = AdminCategoryController.slugOrDerive(request.slug(), request.title());
        requireUniqueSlug(slug, id);

        Instant now = Instant.now();
        StoryContentType contentType = parseEnum(StoryContentType.class, request.contentType(), StoryContentType.TEXT);
        StoryFormat storyFormat = parseEnum(StoryFormat.class, request.storyFormat(), StoryFormat.SERIAL);
        StoryType storyType = parseEnum(StoryType.class, request.storyType(), StoryType.TEXT);
        StoryProgressStatus progressStatus = parseEnum(StoryProgressStatus.class, request.completionStatus(),
                StoryProgressStatus.ONGOING);
        StoryStatus status = parseEnum(StoryStatus.class, request.workflowStatus(), StoryStatus.DRAFT);
        // short_description is VARCHAR(500) while description is TEXT. A synopsis
        // lifted from an uploaded file routinely runs past 500 characters, so the
        // short form is clipped to a teaser and the full text is kept intact in
        // description; without this MySQL rejected the whole insert.
        String synopsis = firstNonBlank(request.synopsis(), request.summary());
        String shortDescription = clip(synopsis, SHORT_DESCRIPTION_LIMIT);
        String description = firstNonBlank(request.summary(), request.synopsis());
        // Bounded columns: an imported file can exceed any of these, and MySQL
        // rejects the whole row rather than trimming.
        String title = clip(request.title().trim(), TITLE_LIMIT);
        String authorName = clip(blankToNull(request.authorName()), TITLE_LIMIT);

        UUID storyId;
        if (id == null) {
            storyId = UUID.randomUUID();
            // Ids are assigned here, so an explicit INSERT is used; repository.save()
            // would treat the populated id as an existing row and emit an UPDATE.
            jdbc.sql("""
                            INSERT INTO stories (id, team_id, created_by, title, slug, original_author,
                                                 short_description, description, cover_url, content_type,
                                                 story_format, story_type, status,
                                                 progress_status, published_at, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(storyId.toString(), teamId.toString(), resolveAuthorUser(teamId).toString(),
                            title, slug, authorName,
                            shortDescription, description, coverUrl, contentType.name(),
                            storyFormat.name(), storyType.name(), status.name(),
                            progressStatus.name(),
                            status == StoryStatus.PUBLISHED ? java.sql.Timestamp.from(now) : null,
                            java.sql.Timestamp.from(now), java.sql.Timestamp.from(now))
                    .update();
        } else {
            Story story = find(id);
            storyId = story.getId();
            story.setTeamId(teamId);
            story.setTitle(title);
            story.setSlug(slug);
            story.setOriginalAuthor(authorName);
            story.setShortDescription(shortDescription);
            story.setDescription(description);
            // A missing upload on edit keeps whatever cover the story already has.
            if (coverUrl != null) {
                story.setCoverUrl(coverUrl);
            }
            story.setContentType(contentType);
            story.setStoryFormat(storyFormat);
            story.setStoryType(storyType);
            story.setProgressStatus(progressStatus);
            story.setStatus(status);
            if (status == StoryStatus.PUBLISHED && story.getPublishedAt() == null) {
                story.setPublishedAt(now);
            }
            story.setUpdatedAt(now);
            storyRepository.save(story);
        }

        List<String> categoryIds = requestedCategoryIds(request);
        replaceGenres(storyId, categoryIds);
        replaceTags(storyId, request.tags());
        return toRow(find(storyId), categoryIds, readTags(storyId));
    }

    /** Accepts the multi-select list, falling back to the legacy single field. */
    private static List<String> requestedCategoryIds(UpsertStoryRequest request) {
        if (request.categoryIds() != null && !request.categoryIds().isEmpty()) {
            return request.categoryIds().stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        }
        return request.categoryId() == null || request.categoryId().isBlank()
                ? List.of()
                : List.of(request.categoryId().trim());
    }

    /**
     * Tags are replaced wholesale so removing one in the drawer removes it here.
     * Slugs are what the catalog filters on; the label keeps the admin's original
     * casing and diacritics for display.
     */
    private void replaceTags(UUID storyId, List<String> tags) {
        jdbc.sql("DELETE FROM story_tags WHERE story_id = ?").param(storyId.toString()).update();
        if (tags == null || tags.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String label = tag.trim();
            String slug = AdminSlugs.slugify(label);
            // Two labels can slugify to the same value ("Ngôn Tình" / "ngon tinh");
            // the primary key would reject the duplicate, so skip it here.
            if (slug.isEmpty() || !seen.add(slug)) {
                continue;
            }
            jdbc.sql("INSERT INTO story_tags (story_id, slug, label) VALUES (?, ?, ?)")
                    .params(storyId.toString(), slug, label)
                    .update();
        }
    }

    /** Splits the tab-joined labels GROUP_CONCAT produced in {@link #LIST_SQL}. */
    private static List<String> splitTags(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split("\t")).map(String::trim).filter(tag -> !tag.isEmpty()).toList();
    }

    private List<String> readTags(UUID storyId) {
        return jdbc.sql("SELECT label FROM story_tags WHERE story_id = ? ORDER BY label")
                .param(storyId.toString())
                .query(String.class)
                .list();
    }

    /** story_genres is a link table, so the admin's single category selection replaces any existing link. */
    private void replaceGenres(UUID storyId, List<String> categoryIds) {
        jdbc.sql("DELETE FROM story_genres WHERE story_id = ?").param(storyId.toString()).update();
        for (String categoryId : categoryIds) {
            UUID genreId = parseUuid(categoryId, "categoryId");
            long exists = jdbc.sql("SELECT COUNT(*) FROM genres WHERE id = ?")
                    .param(genreId.toString()).query(Long.class).optional().orElse(0L);
            if (exists == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "story.invalid_category",
                        "Unknown category",
                        "Thể loại đã chọn không tồn tại hoặc đã bị xóa. Hãy chọn lại thể loại.");
            }
            jdbc.sql("INSERT INTO story_genres (story_id, genre_id) VALUES (?, ?)")
                    .params(storyId.toString(), genreId.toString())
                    .update();
        }
    }

    private UUID resolveAuthorUser(UUID teamId) {
        return jdbc.sql("SELECT user_id FROM team_members WHERE team_id = ? AND status = 'ACTIVE' "
                        + "ORDER BY FIELD(member_role, 'OWNER', 'MANAGER', 'EDITOR', 'MEMBER') LIMIT 1")
                .param(teamId.toString())
                .query(String.class)
                .optional()
                .map(UUID::fromString)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "story.team_without_members",
                        "Team has no active members",
                        "Assign at least one active member to the team before creating a story"));
    }

    private void requireTeamExists(UUID teamId) {
        long exists = jdbc.sql("SELECT COUNT(*) FROM teams WHERE id = ?")
                .param(teamId.toString()).query(Long.class).optional().orElse(0L);
        if (exists == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "story.invalid_team",
                    "Unknown team",
                    "Team đăng truyện không tồn tại hoặc đã bị xóa. Hãy chọn lại team.");
        }
    }

    private void requireUniqueSlug(String slug, UUID excludedId) {
        long taken = excludedId == null
                ? jdbc.sql("SELECT COUNT(*) FROM stories WHERE slug = ?")
                        .param(slug).query(Long.class).optional().orElse(0L)
                : jdbc.sql("SELECT COUNT(*) FROM stories WHERE slug = ? AND id <> ?")
                        .params(slug, excludedId.toString()).query(Long.class).optional().orElse(0L);
        if (taken > 0) {
            // The detail reaches the admin's error popup, so it names the clash
            // and what to do about it rather than just saying "conflict".
            throw new ApiException(HttpStatus.CONFLICT, "story.slug_taken",
                    "Slug already used",
                    "Đường dẫn \"%s\" đã có truyện khác dùng. Hãy đổi slug hoặc đổi tên truyện."
                            .formatted(slug));
        }
    }

    private Story find(UUID id) {
        return storyRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "story.not_found", "Story not found", "Story not found"));
    }

    private AdminStoryRow toRow(Story story, List<String> categoryIds, List<String> tags) {
        String teamName = jdbc.sql("SELECT name FROM teams WHERE id = ?")
                .param(story.getTeamId().toString()).query(String.class).optional().orElse(null);
        List<String> categoryNames = categoryIds.stream()
                .map(id -> jdbc.sql("SELECT name FROM genres WHERE id = ?")
                        .param(id).query(String.class).optional().orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        return new AdminStoryRow(
                story.getId().toString(),
                story.getSlug(),
                story.getTitle(),
                story.getOriginalAuthor(),
                teamName,
                story.getTeamId().toString(),
                // The first genre keeps the single-value column populated.
                categoryIds.isEmpty() ? null : categoryIds.get(0),
                categoryNames.isEmpty() ? null : categoryNames.get(0),
                categoryIds,
                categoryNames,
                story.getCoverUrl(),
                story.getShortDescription(),
                tags == null ? List.of() : tags,
                (story.getStoryFormat() == null ? StoryFormat.SERIAL : story.getStoryFormat()).name(),
                (story.getStoryType() == null ? StoryType.TEXT : story.getStoryType()).name(),
                story.getStatus().name(),
                story.getProgressStatus().name(),
                story.getUpdatedAt() == null ? null : story.getUpdatedAt().toString()
        );
    }

    /**
     * Chapters submitted from the drawer replace the story's existing set. Rows
     * that were already purchased are kept so unlock history stays valid.
     */
    private void replaceChapters(UUID storyId, List<ChapterDraft> chapters) {
        if (chapters.isEmpty()) {
            return;
        }

        UUID createdBy = jdbc.sql("SELECT created_by FROM stories WHERE id = ?")
                .param(storyId.toString()).query(String.class).single().transform(UUID::fromString);

        jdbc.sql("""
                        DELETE FROM chapters
                        WHERE story_id = ?
                          AND id NOT IN (SELECT chapter_id FROM chapter_unlocks)
                        """)
                .param(storyId.toString())
                .update();

        Instant now = Instant.now();
        int number = 1;
        Set<String> usedSlugs = new LinkedHashSet<>();
        for (ChapterDraft chapter : chapters) {
            // Titles and slugs come from an uploaded file, so both are clipped to
            // their column widths; MySQL would otherwise reject the whole chapter.
            String title = clip(
                    chapter.title() == null || chapter.title().isBlank()
                            ? "Chương " + number
                            : chapter.title(),
                    TITLE_LIMIT);
            String slug = chapter.slug() == null || chapter.slug().isBlank()
                    ? AdminSlugs.slugify(title)
                    : chapter.slug().trim();
            if (slug.isEmpty()) {
                slug = "chuong-" + number;
            }
            slug = clip(slug, CHAPTER_SLUG_LIMIT);
            // chapters.slug is unique per story. Clipping can make two long
            // titles collide, so only a repeat gets the chapter number appended -
            // slugs that were already distinct keep the URLs they had.
            if (!usedSlugs.add(slug)) {
                slug = clip(slug, CHAPTER_SLUG_LIMIT - 12) + "-" + number;
                usedSlugs.add(slug);
            }

            String accessTypeVal = "PAID".equalsIgnoreCase(chapter.accessType()) ? "PAID" : "FREE";
            long coinPriceVal = (chapter.coinPrice() != null && chapter.coinPrice() > 0) ? chapter.coinPrice() : 0L;

            jdbc.sql("""
                            INSERT INTO chapters (id, story_id, chapter_number, title, slug, content,
                                                  access_type, coin_price, status, published_at,
                                                  created_by, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                                title = VALUES(title),
                                content = VALUES(content),
                                access_type = VALUES(access_type),
                                coin_price = VALUES(coin_price),
                                updated_at = VALUES(updated_at)
                            """)
                    .params(UUID.randomUUID().toString(), storyId.toString(), number, title, slug,
                            chapter.content(), accessTypeVal, coinPriceVal, java.sql.Timestamp.from(now), createdBy.toString(),
                            java.sql.Timestamp.from(now), java.sql.Timestamp.from(now))
                    .update();
            number++;
        }

        jdbc.sql("UPDATE stories SET last_chapter_at = ?, updated_at = ? WHERE id = ?")
                .params(java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), storyId.toString())
                .update();
    }

    /**
     * Reads chapter parts in {@code chapters[i].field} form. When a chapter has an
     * attached file its bytes win over the inline textarea value.
     */
    private List<ChapterDraft> readChapters(MultipartHttpServletRequest request) {
        List<ChapterDraft> chapters = new ArrayList<>();
        for (int index = 0; index < MAX_CHAPTERS_PER_REQUEST; index++) {
            String prefix = "chapters[" + index + "].";
            String title = formValue(request, prefix + "title");
            String slug = formValue(request, prefix + "slug");
            String content = formValue(request, prefix + "content");
            String accessType = formValue(request, prefix + "accessType");
            String coinPriceStr = formValue(request, prefix + "coinPrice");
            Long coinPrice = null;
            if (coinPriceStr != null && !coinPriceStr.isBlank()) {
                try {
                    coinPrice = Long.parseLong(coinPriceStr.trim());
                } catch (NumberFormatException e) {
                    coinPrice = 0L;
                }
            }

            MultipartFile file = request.getFile(prefix + "file");
            if (file != null && !file.isEmpty()) {
                content = storyMedia.readTextFile(file);
            }
            if (title == null && content == null) {
                break;
            }
            if (content == null || content.isBlank()) {
                continue;
            }
            chapters.add(new ChapterDraft(title, slug, content, accessType, coinPrice));
        }
        return chapters;
    }

    private static UpsertStoryRequest fromForm(MultipartHttpServletRequest request) {
        List<String> tags = new ArrayList<>();
        String rawTags = formValue(request, "tags");
        if (rawTags != null && !rawTags.isBlank()) {
            tags.addAll(Arrays.stream(rawTags.split(",")).map(String::trim).filter(tag -> !tag.isEmpty()).toList());
        }
        // Sent as one comma-joined field so a many-genre story does not consume
        // one multipart part per genre.
        List<String> categoryIds = new ArrayList<>();
        String rawCategories = formValue(request, "categoryIds");
        if (rawCategories != null && !rawCategories.isBlank()) {
            categoryIds.addAll(Arrays.stream(rawCategories.split(","))
                    .map(String::trim).filter(id -> !id.isEmpty()).toList());
        }
        return new UpsertStoryRequest(
                formValue(request, "title"),
                formValue(request, "slug"),
                formValue(request, "authorName"),
                formValue(request, "teamId"),
                formValue(request, "categoryId"),
                categoryIds,
                formValue(request, "synopsis"),
                formValue(request, "summary"),
                formValue(request, "contentType"),
                formValue(request, "storyFormat"),
                formValue(request, "storyType"),
                formValue(request, "workflowStatus"),
                formValue(request, "completionStatus"),
                tags
        );
    }

    private static String formValue(MultipartHttpServletRequest request, String name) {
        return request.getParameter(name);
    }

    private record ChapterDraft(String title, String slug, String content, String accessType, Long coinPrice) {}

    static UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "request.invalid",
                    "Invalid identifier", field + " must be a valid UUID");
        }
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "request.invalid",
                    "Invalid value", value + " is not a supported " + type.getSimpleName());
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Trims a value to a bounded column's width. MySQL rejects an over-long
     * value outright rather than truncating it, which failed the entire story
     * insert; clipping keeps the save working while the untruncated text lives
     * on in the matching TEXT column.
     */
    private static String clip(String value, int limit) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        // Prefer cutting at a word boundary so the teaser does not end mid-word.
        String head = trimmed.substring(0, limit - 1);
        int lastSpace = head.lastIndexOf(' ');
        String body = lastSpace > limit / 2 ? head.substring(0, lastSpace) : head;
        return body + "…";
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private static String instantText(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
}
