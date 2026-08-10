package com.storyplatform.admin.api;

import com.storyplatform.admin.application.StoryMediaStorage;
import com.storyplatform.admin.application.dto.AdminDtos.AdminStoryRow;
import com.storyplatform.admin.application.dto.AdminDtos.UpsertStoryRequest;
import com.storyplatform.catalog.domain.Story;
import com.storyplatform.catalog.domain.StoryContentType;
import com.storyplatform.catalog.domain.StoryProgressStatus;
import com.storyplatform.catalog.domain.StoryStatus;
import com.storyplatform.catalog.infrastructure.StoryRepository;
import com.storyplatform.shared.api.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
                   s.status, s.progress_status, s.updated_at, s.team_id,
                   t.name AS team_name,
                   (SELECT g.id FROM story_genres sg
                     JOIN genres g ON g.id = sg.genre_id
                     WHERE sg.story_id = s.id ORDER BY g.name LIMIT 1) AS genre_id,
                   (SELECT g.name FROM story_genres sg
                     JOIN genres g ON g.id = sg.genre_id
                     WHERE sg.story_id = s.id ORDER BY g.name LIMIT 1) AS genre_name
            FROM stories s
            LEFT JOIN teams t ON t.id = s.team_id
            ORDER BY s.updated_at DESC
            """;

    /** Upper bound on the chapters[i] indexes scanned out of a multipart request. */
    private static final int MAX_CHAPTERS_PER_REQUEST = 500;

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
                        rs.getString("cover_url"),
                        rs.getString("short_description"),
                        List.of(),
                        rs.getString("status"),
                        rs.getString("progress_status"),
                        instantText(rs.getTimestamp("updated_at"))
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
        StoryProgressStatus progressStatus = parseEnum(StoryProgressStatus.class, request.completionStatus(),
                StoryProgressStatus.ONGOING);
        StoryStatus status = parseEnum(StoryStatus.class, request.workflowStatus(), StoryStatus.DRAFT);
        String shortDescription = firstNonBlank(request.synopsis(), request.summary());
        String description = firstNonBlank(request.summary(), request.synopsis());

        UUID storyId;
        if (id == null) {
            storyId = UUID.randomUUID();
            // Ids are assigned here, so an explicit INSERT is used; repository.save()
            // would treat the populated id as an existing row and emit an UPDATE.
            jdbc.sql("""
                            INSERT INTO stories (id, team_id, created_by, title, slug, original_author,
                                                 short_description, description, cover_url, content_type, status,
                                                 progress_status, published_at, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(storyId.toString(), teamId.toString(), resolveAuthorUser(teamId).toString(),
                            request.title().trim(), slug, blankToNull(request.authorName()),
                            shortDescription, description, coverUrl, contentType.name(), status.name(),
                            progressStatus.name(),
                            status == StoryStatus.PUBLISHED ? java.sql.Timestamp.from(now) : null,
                            java.sql.Timestamp.from(now), java.sql.Timestamp.from(now))
                    .update();
        } else {
            Story story = find(id);
            storyId = story.getId();
            story.setTeamId(teamId);
            story.setTitle(request.title().trim());
            story.setSlug(slug);
            story.setOriginalAuthor(blankToNull(request.authorName()));
            story.setShortDescription(shortDescription);
            story.setDescription(description);
            // A missing upload on edit keeps whatever cover the story already has.
            if (coverUrl != null) {
                story.setCoverUrl(coverUrl);
            }
            story.setContentType(contentType);
            story.setProgressStatus(progressStatus);
            story.setStatus(status);
            if (status == StoryStatus.PUBLISHED && story.getPublishedAt() == null) {
                story.setPublishedAt(now);
            }
            story.setUpdatedAt(now);
            storyRepository.save(story);
        }

        replaceGenre(storyId, request.categoryId());
        return toRow(find(storyId), request.categoryId(), request.tags());
    }

    /** story_genres is a link table, so the admin's single category selection replaces any existing link. */
    private void replaceGenre(UUID storyId, String categoryId) {
        jdbc.sql("DELETE FROM story_genres WHERE story_id = ?").param(storyId.toString()).update();
        if (categoryId == null || categoryId.isBlank()) {
            return;
        }
        UUID genreId = parseUuid(categoryId, "categoryId");
        long exists = jdbc.sql("SELECT COUNT(*) FROM genres WHERE id = ?")
                .param(genreId.toString()).query(Long.class).optional().orElse(0L);
        if (exists == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "story.invalid_category",
                    "Unknown category", "categoryId does not match an existing genre");
        }
        jdbc.sql("INSERT INTO story_genres (story_id, genre_id) VALUES (?, ?)")
                .params(storyId.toString(), genreId.toString())
                .update();
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
                    "Unknown team", "teamId does not match an existing team");
        }
    }

    private void requireUniqueSlug(String slug, UUID excludedId) {
        long taken = excludedId == null
                ? jdbc.sql("SELECT COUNT(*) FROM stories WHERE slug = ?")
                        .param(slug).query(Long.class).optional().orElse(0L)
                : jdbc.sql("SELECT COUNT(*) FROM stories WHERE slug = ? AND id <> ?")
                        .params(slug, excludedId.toString()).query(Long.class).optional().orElse(0L);
        if (taken > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "story.slug_taken",
                    "Slug already used", "Slug already used by another story");
        }
    }

    private Story find(UUID id) {
        return storyRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "story.not_found", "Story not found", "Story not found"));
    }

    private AdminStoryRow toRow(Story story, String categoryId, List<String> tags) {
        String teamName = jdbc.sql("SELECT name FROM teams WHERE id = ?")
                .param(story.getTeamId().toString()).query(String.class).optional().orElse(null);
        String categoryName = categoryId == null || categoryId.isBlank() ? null
                : jdbc.sql("SELECT name FROM genres WHERE id = ?")
                        .param(categoryId).query(String.class).optional().orElse(null);

        return new AdminStoryRow(
                story.getId().toString(),
                story.getSlug(),
                story.getTitle(),
                story.getOriginalAuthor(),
                teamName,
                story.getTeamId().toString(),
                categoryId,
                categoryName,
                story.getCoverUrl(),
                story.getShortDescription(),
                tags == null ? List.of() : tags,
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
        for (ChapterDraft chapter : chapters) {
            String title = chapter.title() == null || chapter.title().isBlank()
                    ? "Chương " + number
                    : chapter.title().trim();
            String slug = chapter.slug() == null || chapter.slug().isBlank()
                    ? AdminSlugs.slugify(title)
                    : chapter.slug().trim();
            if (slug.isEmpty()) {
                slug = "chuong-" + number;
            }

            jdbc.sql("""
                            INSERT INTO chapters (id, story_id, chapter_number, title, slug, content,
                                                  access_type, coin_price, status, published_at,
                                                  created_by, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, 'FREE', 0, 'PUBLISHED', ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                                title = VALUES(title),
                                content = VALUES(content),
                                updated_at = VALUES(updated_at)
                            """)
                    .params(UUID.randomUUID().toString(), storyId.toString(), number, title, slug,
                            chapter.content(), java.sql.Timestamp.from(now), createdBy.toString(),
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
            chapters.add(new ChapterDraft(title, slug, content));
        }
        return chapters;
    }

    private static UpsertStoryRequest fromForm(MultipartHttpServletRequest request) {
        List<String> tags = new ArrayList<>();
        String rawTags = formValue(request, "tags");
        if (rawTags != null && !rawTags.isBlank()) {
            tags.addAll(Arrays.stream(rawTags.split(",")).map(String::trim).filter(tag -> !tag.isEmpty()).toList());
        }
        return new UpsertStoryRequest(
                formValue(request, "title"),
                formValue(request, "slug"),
                formValue(request, "authorName"),
                formValue(request, "teamId"),
                formValue(request, "categoryId"),
                formValue(request, "synopsis"),
                formValue(request, "summary"),
                formValue(request, "contentType"),
                formValue(request, "workflowStatus"),
                formValue(request, "completionStatus"),
                tags
        );
    }

    private static String formValue(MultipartHttpServletRequest request, String name) {
        return request.getParameter(name);
    }

    private record ChapterDraft(String title, String slug, String content) {}

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
