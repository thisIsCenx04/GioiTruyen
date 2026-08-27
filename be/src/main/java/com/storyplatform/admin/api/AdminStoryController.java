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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AdminStoryController.class);

    /**
     * A story can carry several genres; the admin table shows a single category,
     * so the first one is picked in a subquery. Joining genres directly would
     * duplicate story rows and break only_full_group_by.
     */
    private static final String LIST_SQL = """
            SELECT s.id, s.slug, s.title, s.original_author,
                   -- Cả teaser 500 ký tự lẫn văn án đầy đủ. Bảng hiển thị
                   -- teaser, còn form sửa truyện phải nạp bản đầy đủ: nạp
                   -- teaser rồi lưu lại là thứ đã cắt cụt văn án của mọi truyện
                   -- xuống 500 ký tự ngay lần sửa đầu tiên.
                   s.short_description, s.description, s.cover_url,
                   s.story_format, s.story_type, s.status, s.progress_status,
                   s.combo_price_xu,
                   s.created_at, s.updated_at, s.team_id,
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
                     WHERE sg.story_id = s.id) AS genre_names,
                   (SELECT COUNT(*) FROM chapters c WHERE c.story_id = s.id) AS chapter_count
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

    /**
     * How long a synopsis may be, in characters.
     *
     * <p>stories.description is MEDIUMTEXT (16 MB, about 5.5 million Vietnamese
     * characters), so this is a product decision rather than a column limit -
     * which is the point: the publisher is told the number rather than having
     * MySQL refuse the row for a reason nothing on screen explains.
     */
    private static final int SYNOPSIS_LIMIT = 100_000;

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
            JdbcClient jdbc) {
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
                        rs.getString("description"),
                        splitTags(rs.getString("tag_labels")),
                        rs.getString("story_format"),
                        rs.getString("story_type"),
                        rs.getString("status"),
                        rs.getString("progress_status"),
                        rs.getInt("chapter_count"),
                        // getLong reads SQL NULL as 0, and 0 would look like a
                        // configured free combo instead of "no bundle deal".
                        rs.getObject("combo_price_xu") == null ? null : rs.getLong("combo_price_xu"),
                        instantText(rs.getTimestamp("updated_at")),
                        instantText(rs.getTimestamp("created_at"))))
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
                        rs.getString("status")))
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
        // Chapters are replaced wholesale, so an edit that submits an incomplete
        // list would delete the rest. Only an explicit opt-in touches them; a
        // plain metadata edit leaves the existing chapters alone.
        if (id == null || "true".equalsIgnoreCase(request.getParameter("replaceChapters"))) {
            UUID storyId = UUID.fromString(row.id());
            // Set by the form only when the editor removed chapters on purpose
            // and confirmed it. Without it a shorter list is treated as an
            // accident and refused; see checkNoChapterLoss.
            boolean allowDeletion =
                    "true".equalsIgnoreCase(request.getParameter("allowChapterDeletion"));
            replaceChapters(storyId, readChapters(request), allowDeletion);
            // `row` was built before the chapters existed, so its chapterCount was
            // whatever the story had beforehand - zero for a new story, however
            // many chapters were just uploaded. Rebuilt here so the caller gets the
            // count it actually saved.
            return toRow(find(storyId), readCategoryIds(storyId), readTags(storyId));
        }
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

    /**
     * Removes a story and its chapters for good.
     *
     * <p>
     * Hiding is the everyday action and stays the default; this exists for
     * mistakes and duplicates. A story anyone has paid to read is refused
     * instead: deleting it would strip chapters from readers who bought them
     * and leave the purchase records pointing at nothing.
     */
    @DeleteMapping("/{id}/permanent")
    @Transactional
    public void deletePermanently(@PathVariable UUID id) {
        Story story = find(id);

        // Both are checked: purchase_orders is ON DELETE RESTRICT, so a paid
        // story would otherwise fail on a foreign key with an opaque message.
        long unlocks = jdbc.sql("""
                SELECT (SELECT COUNT(*) FROM chapter_unlocks u
                        JOIN chapters c ON c.id = u.chapter_id
                        WHERE c.story_id = :storyId)
                     + (SELECT COUNT(*) FROM purchase_orders WHERE story_id = :storyId)
                """)
                .param("storyId", id.toString())
                .query(Long.class)
                .optional()
                .orElse(0L);
        if (unlocks > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "story.has_purchases",
                    "Story has purchased chapters",
                    ("Truyện \"%s\" đã có %d giao dịch trả phí nên không thể xóa. "
                            + "Hãy dùng \"Ngừng hiển thị\" để ẩn truyện khỏi người đọc.")
                            .formatted(story.getTitle(), unlocks));
        }

        // story_genres, story_tags and chapters cascade from the stories row.
        jdbc.sql("DELETE FROM stories WHERE id = ?").param(id.toString()).update();
    }

    private AdminStoryRow save(UUID id, UpsertStoryRequest request) {
        return save(id, request, null);
    }

    private AdminStoryRow save(UUID id, UpsertStoryRequest request, String coverUrl) {
        AdminCategoryController.requireText(request.title(), "title");
        AdminCategoryController.requireText(request.teamId(), "teamId");

        UUID teamId = parseUuid(request.teamId(), "teamId");
        requireTeamExists(teamId);

        // A story keeps the address it was published at. The slug used to be
        // re-derived from the title on every save, and the publisher form sends
        // no slug of its own - so correcting a typo in the title silently moved
        // the story: every link, bookmark and search result pointing at the old
        // address started answering 404. Only an explicit new slug moves it.
        String slug = id == null
                ? AdminCategoryController.slugOrDerive(request.slug(), request.title())
                : blankToNull(request.slug()) == null
                        ? find(id).getSlug()
                        : AdminCategoryController.slugOrDerive(request.slug(), request.title());
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
        // Refused rather than trimmed. Silently clipping a synopsis is how the
        // form used to destroy them - the publisher saw the save succeed and
        // only found the missing text later, by which point the original was
        // gone. The column holds far more than this; the limit is the product's.
        if (description != null && description.length() > SYNOPSIS_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "story.synopsis_too_long",
                    "Synopsis too long",
                    ("Giới thiệu truyện dài %,d ký tự, vượt giới hạn %,d ký tự. "
                            + "Hãy rút ngắn bớt rồi lưu lại.")
                            .formatted(description.length(), SYNOPSIS_LIMIT));
        }
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
            // Giới thiệu chỉ đổi khi lệnh gọi thật sự mang phần giới thiệu.
            //
            // Hai cách hỏng đã xảy ra, và cả hai đều âm thầm:
            //
            //   1. Form không có ô giới thiệu (màn sửa chương chẳng hạn) gửi lên
            //      một giá trị rỗng, và cả văn án bị xoá trắng.
            //   2. Form nạp nhầm short_description - bản rút gọn 500 ký tự - rồi
            //      lưu đè lên bản đầy đủ. Trong CSDL còn nguyên dấu vết: những
            //      truyện có description dài đúng bằng short_description ở mức
            //      496-499 ký tự chính là các nạn nhân, và bản gốc mất hẳn.
            //
            // Chương và giới thiệu không liên quan gì nhau, nên sửa cái này
            // không được phép chạm vào cái kia.
            if (description != null && !description.isBlank()) {
                String existing = story.getDescription();
                if (isTeaserOverwrite(existing, story.getShortDescription(), description)) {
                    log.warn("Bỏ qua giới thiệu rút gọn ghi đè bản đầy đủ của truyện {} ({} -> {} ký tự)",
                            storyId, existing.length(), description.length());
                } else {
                    story.setShortDescription(shortDescription);
                    story.setDescription(description);
                }
            }
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

        // Written through JDBC rather than the entity: the combo price is a
        // monetisation setting read by MonetizationFlowService, not part of the
        // Story aggregate the repository maps.
        //
        // Chỉ ghi khi lệnh gọi thực sự nói về combo. Sửa chương, đổi thể loại
        // hay bất kỳ việc gì khác đều không đụng tới giá combo - hai thứ đó
        // không liên quan gì nhau, và gộp chúng vào một câu UPDATE là lý do một
        // truyện mở bán combo từ lâu bỗng "mất tiêu" sau một lần thêm chương.
        if (request.comboPriceXu() != null) {
            jdbc.sql("UPDATE stories SET combo_price_xu = ? WHERE id = ?")
                    .params(request.comboPriceXu() > 0 ? request.comboPriceXu() : null, storyId.toString())
                    .update();
        }

        List<String> categoryIds = replaceGenres(storyId, requestedCategoryIds(request));
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

    /** The genres currently linked to a story, for rebuilding a row from the DB. */
    private List<String> readCategoryIds(UUID storyId) {
        return jdbc.sql("SELECT genre_id FROM story_genres WHERE story_id = ?")
                .param(storyId.toString())
                .query(String.class)
                .list();
    }

    private List<String> readTags(UUID storyId) {
        return jdbc.sql("SELECT label FROM story_tags WHERE story_id = ? ORDER BY label")
                .param(storyId.toString())
                .query(String.class)
                .list();
    }

    /**
     * story_genres is a link table, so the admin's single category selection
     * replaces any existing link.
     */
    private List<String> replaceGenres(UUID storyId, List<String> categoryIds) {
        jdbc.sql("DELETE FROM story_genres WHERE story_id = ?").param(storyId.toString()).update();
        List<String> linked = new java.util.ArrayList<>();
        for (String categoryId : categoryIds) {
            UUID genreId = parseUuid(categoryId, "categoryId");
            long exists = jdbc.sql("SELECT COUNT(*) FROM genres WHERE id = ?")
                    .param(genreId.toString()).query(Long.class).optional().orElse(0L);
            // Thể loại đã bị xoá thì bỏ qua, không chặn cả lần lưu.
            //
            // Trước đây chỗ này ném lỗi, nên một thể loại bị quản trị viên xoá
            // trong lúc người dùng đang soạn là đủ để cả bản nháp bảy trăm
            // chương không lưu được - và thông báo lại bảo họ "chọn lại thể
            // loại", việc mà form đang mở không cho làm. Mất một liên kết thể
            // loại là chuyện sửa sau bằng một cú bấm; mất cả buổi nhập liệu
            // thì không.
            if (exists == 0) {
                log.warn("Bỏ qua thể loại không còn tồn tại {} khi lưu truyện {}", genreId, storyId);
                continue;
            }
            jdbc.sql("INSERT INTO story_genres (story_id, genre_id) VALUES (?, ?)")
                    .params(storyId.toString(), genreId.toString())
                    .update();
            linked.add(genreId.toString());
        }
        return linked;
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

        int chapterCount = jdbc.sql("SELECT COUNT(*) FROM chapters WHERE story_id = ?")
                .param(story.getId().toString())
                .query(Integer.class)
                .single();

        Long comboPriceXu = jdbc.sql("SELECT combo_price_xu FROM stories WHERE id = ?")
                .param(story.getId().toString())
                .query(Long.class)
                .optional()
                .orElse(null);

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
                story.getDescription(),
                tags == null ? List.of() : tags,
                (story.getStoryFormat() == null ? StoryFormat.SERIAL : story.getStoryFormat()).name(),
                (story.getStoryType() == null ? StoryType.TEXT : story.getStoryType()).name(),
                story.getStatus().name(),
                story.getProgressStatus().name(),
                chapterCount,
                comboPriceXu,
                story.getUpdatedAt() == null ? null : story.getUpdatedAt().toString(),
                story.getCreatedAt() == null ? null : story.getCreatedAt().toString());
    }

    /**
     * A story with a combo price cannot grow.
     *
     * <p>The combo sells "every chapter of this story" for one figure. Adding a
     * chapter afterwards hands it free to everyone who already bought the
     * bundle, while the next buyer pays the same price for more - so the same
     * offer means two different things depending on when it was taken. The
     * publishing form warns before a combo is set; this is the guard that holds
     * whatever the form does, including for callers that skip it.
     *
     * <p>Editing existing chapters and removing them stay allowed: neither
     * changes what a past buyer was promised.
     */
    private void requireComboAllowsChapterCount(UUID storyId, int existing, int incoming) {
        if (incoming <= existing) {
            return;
        }
        Long comboPrice = jdbc.sql("SELECT combo_price_xu FROM stories WHERE id = ?")
                .param(storyId.toString())
                .query(Long.class)
                .optional()
                .orElse(null);
        if (comboPrice == null || comboPrice <= 0) {
            return;
        }
        throw new ApiException(HttpStatus.CONFLICT, "story.combo_locked",
                "Combo locks the chapter list",
                ("Truyện đang bán Combo Full nên không thể thêm chương mới "
                        + "(hiện %d chương, đang lưu %d chương). Hãy bỏ giá Combo trước, "
                        + "hoặc giữ nguyên số chương.").formatted(existing, incoming));
    }

    /**
     * Writes the submitted chapter list onto the story, matching each draft to
     * the chapter it is editing.
     *
     * <p>This used to delete every chapter and insert the list afresh, which was
     * wrong in three ways at once. New rows meant new ids, so reading progress
     * and anything else pointing at a chapter lost its target. Numbering
     * restarted at 1, so a story whose chapters were titled "Chương 2" upwards
     * came back with the title and the number disagreeing. And an already-bought
     * chapter was exempt from the delete but not from the re-insert, so
     * {@code ON DUPLICATE KEY} overwrote it with whatever draft now sat at its
     * number - a reader's purchased chapter silently becoming another chapter's
     * text.
     *
     * <p>Identity is the chapter id the form sends back. A draft carrying one
     * updates that row in place and keeps its number; a draft without one is new.
     * Chapters the publisher removed are deleted, except where someone has paid
     * for them.
     */
    private void replaceChapters(UUID storyId, List<ChapterDraft> chapters, boolean allowDeletion) {
        UUID createdBy = jdbc.sql("SELECT created_by FROM stories WHERE id = ?")
                .param(storyId.toString()).query(String.class).single().transform(UUID::fromString);

        List<ExistingChapter> existingRows = jdbc.sql(
                        "SELECT id, chapter_number FROM chapters WHERE story_id = ? ORDER BY chapter_number")
                .param(storyId.toString())
                .query((rs, rowNum) -> new ExistingChapter(
                        rs.getString("id"), rs.getBigDecimal("chapter_number")))
                .list();
        Map<String, ExistingChapter> byId = new LinkedHashMap<>();
        for (ExistingChapter row : existingRows) {
            byId.put(row.id(), row);
        }

        int existing = existingRows.size();
        checkNoChapterLoss(existing, chapters.size(), allowDeletion);
        requireComboAllowsChapterCount(storyId, existing, chapters.size());

        // Anything the publisher dropped from the list. A chapter someone has
        // already paid for is kept regardless: deleting it would take away what
        // they bought, and the unlock row references it.
        Set<String> submittedIds = chapters.stream()
                .map(ChapterDraft::id)
                .filter(id -> id != null && byId.containsKey(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> removed = existingRows.stream()
                .map(ExistingChapter::id)
                .filter(id -> !submittedIds.contains(id))
                .toList();
        for (String id : removed) {
            jdbc.sql("""
                    DELETE FROM chapters
                    WHERE id = ? AND story_id = ?
                      AND id NOT IN (SELECT chapter_id FROM chapter_unlocks)
                    """)
                    .params(id, storyId.toString())
                    .update();
        }

        if (chapters.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        java.sql.Timestamp stamp = java.sql.Timestamp.from(now);

        // Numbers are assigned by position, so the list the publisher sees is the
        // order readers get. Renumbering in place would collide with
        // UNIQUE(story_id, chapter_number) halfway through, so every row is first
        // parked on a negative number no final value can reach.
        jdbc.sql("UPDATE chapters SET chapter_number = -chapter_number - 1 WHERE story_id = ? AND chapter_number >= 0")
                .param(storyId.toString())
                .update();
        // Slugs are unique per story too, and for the same reason.
        jdbc.sql("UPDATE chapters SET slug = CONCAT('tmp-', id) WHERE story_id = ?")
                .param(storyId.toString())
                .update();

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
            // A PAID chapter priced at zero would unlock for free, so the two
            // fields are checked against each other rather than separately.
            if ("PAID".equals(accessTypeVal) && coinPriceVal <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "chapter.paid_needs_price",
                        "Paid chapter has no price",
                        "Chương \"%s\" đang để trả phí nhưng giá bằng 0. Hãy nhập giá lớn hơn 0 xu."
                                .formatted(title));
            }

            String existingId = chapter.id() != null && byId.containsKey(chapter.id()) ? chapter.id() : null;
            if (existingId != null) {
                jdbc.sql("""
                        UPDATE chapters
                           SET chapter_number = ?, title = ?, slug = ?, content = ?,
                               access_type = ?, coin_price = ?, updated_at = ?
                         WHERE id = ? AND story_id = ?
                        """)
                        .params(number, title, slug, chapter.content(), accessTypeVal, coinPriceVal,
                                stamp, existingId, storyId.toString())
                        .update();
            } else {
                jdbc.sql("""
                        INSERT INTO chapters (id, story_id, chapter_number, title, slug, content,
                                              access_type, coin_price, status, published_at,
                                              created_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?, ?, ?)
                        """)
                        .params(UUID.randomUUID().toString(), storyId.toString(), number, title, slug,
                                chapter.content(), accessTypeVal, coinPriceVal, stamp,
                                createdBy.toString(), stamp, stamp)
                        .update();
            }
            number++;
        }

        // Any chapter still parked on a negative number was kept only because it
        // has been paid for. It keeps its text and its buyer, and is placed after
        // the submitted list rather than left on an impossible number.
        List<String> parked = jdbc.sql(
                        "SELECT id FROM chapters WHERE story_id = ? AND chapter_number < 0 ORDER BY chapter_number DESC")
                .param(storyId.toString())
                .query(String.class)
                .list();
        for (String id : parked) {
            jdbc.sql("UPDATE chapters SET chapter_number = ?, slug = ?, updated_at = ? WHERE id = ?")
                    .params(number, clip("chuong-" + number + "-" + id, CHAPTER_SLUG_LIMIT), stamp, id)
                    .update();
            number++;
        }

        jdbc.sql("UPDATE stories SET last_chapter_at = ?, updated_at = ? WHERE id = ?")
                .params(stamp, stamp, storyId.toString())
                .update();
    }

    /** A chapter as it stands in the database before the submitted list is applied. */
    private record ExistingChapter(String id, java.math.BigDecimal chapterNumber) {}

    /**
     * Reads chapter parts in {@code chapters[i].field} form. When a chapter has an
     * attached file its bytes win over the inline textarea value.
     */
    private List<ChapterDraft> readChapters(MultipartHttpServletRequest request) {
        List<ChapterDraft> chapters = new ArrayList<>();
        for (int index = 0; index < MAX_CHAPTERS_PER_REQUEST; index++) {
            String prefix = "chapters[" + index + "].";
            String chapterId = formValue(request, prefix + "id");
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
            // Dropping an empty chapter here made it vanish without a word: the
            // submitted list simply came back one chapter shorter than what the
            // admin saw in the form. Refusing says which chapter is at fault.
            if (content == null || content.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "chapter.empty_content",
                        "Chapter has no content",
                        "Chương %d (\"%s\") chưa có nội dung nên không thể lưu. Hãy nhập nội dung hoặc xóa chương này."
                                .formatted(index + 1, title == null ? "" : title));
            }
            chapters.add(new ChapterDraft(
                    chapterId == null || chapterId.isBlank() ? null : chapterId.trim(),
                    title, slug, content, accessType, coinPrice));
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
                tags,
                parseCoin(formValue(request, "comboPriceXu")));
    }

    /**
     * True khi giới thiệu gửi lên chính là bản teaser đang lưu, ngắn hơn văn án
     * đầy đủ - nghĩa là form đã nạp nhầm {@code short_description} và sắp ghi đè
     * lên {@code description}.
     *
     * <p>Dấu vết trong CSDL rất rõ: những truyện có description dài đúng bằng
     * short_description ở mức 496-499 ký tự là các nạn nhân, và bản gốc mất hẳn.
     * Một lần lưu nhầm là mất vĩnh viễn, nên chặn ở đây chứ không phải sửa sau.
     *
     * <p>Giỏ ghi đè hợp lệ - người dùng thật sự muốn rút ngắn văn án - không trùng
     * kiểu này, vì bản mới khi đó không giống hệt teaser cũ từng ký tự.
     */
    public static boolean isTeaserOverwrite(String existingDescription,
                                            String existingShortDescription,
                                            String incomingDescription) {
        if (existingDescription == null || incomingDescription == null
                || existingDescription.length() <= incomingDescription.length()) {
            return false;
        }
        if (incomingDescription.equals(existingShortDescription)) {
            return true;
        }
        // Bản clip cắt đúng ở mốc 500 ký tự cũng là teaser, kể cả khi
        // short_description trong CSDL đã lệch đi (truyện cũ, hoặc teaser được
        // sinh lại sau một lần sửa khác). Dấu hiệu: bản gửi lên là tiền tố
        // nguyên vẹn của bản đang lưu và dừng ngay tại mốc cắt.
        //
        // Điều kiện "tiền tố nguyên vẹn" là thứ giữ cho việc rút gọn văn án
        // thật sự vẫn chạy: người viết lại văn án ngắn hơn gần như không bao
        // giờ gõ ra đúng từng ký tự phần đầu của bản cũ rồi dừng ở ký tự thứ
        // 500.
        return incomingDescription.length() == SHORT_DESCRIPTION_LIMIT
                && existingDescription.startsWith(incomingDescription);
    }

    /**
     * Giá combo gửi lên, hoặc null khi lệnh gọi không nói gì về combo.
     *
     * <p>Phân biệt "không gửi" với "gửi số 0" là điều kiện để việc sửa chương
     * không xoá mất combo. Trước đây cả hai đều thành null, và câu UPDATE chạy
     * vô điều kiện trên mọi lần lưu - nên một form không có ô combo là đủ để
     * xoá sạch giá của một truyện đã mở bán từ lâu.
     *
     * <ul>
     *   <li>null - không gửi, giữ nguyên giá đang có.</li>
     *   <li>0 - cố ý gỡ combo.</li>
     *   <li>số dương - đặt giá đó.</li>
     * </ul>
     */
    public static Long parseCoin(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : 0L;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String formValue(MultipartHttpServletRequest request, String name) {
        return request.getParameter(name);
    }

    /**
     * @param id the chapter this draft is editing, or null when it is new. This
     *           is what makes an edit land on the row the publisher was looking
     *           at rather than on whatever now sits at the same position.
     */
    private record ChapterDraft(String id, String title, String slug, String content,
                                String accessType, Long coinPrice) {
    }

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

    /**
     * Refuses a re-upload that would leave the story with fewer chapters.
     *
     * <p>Replacing a story's chapters deletes what was there first, so an upload
     * that came up short - a truncated file, the wrong export, a failed parse -
     * would silently destroy the difference. A 792-chapter story re-uploaded
     * from a 20-chapter file loses 772 chapters, and nothing would say so.
     *
     * <p>Growing or replacing like for like is ordinary editing and passes.
     *
     * <p>Deliberate deletion passes too, but only when the caller says so. The
     * guard used to refuse every short list, which made removing a chapter
     * impossible: the form has a delete button per chapter, pressing it and
     * saving submitted one chapter fewer, and the story came back refused - with
     * a message advising the publisher to do the very thing that had just been
     * blocked. The flag is what separates "I meant this" from a truncated file.
     *
     * @param existingCount   chapters the story has now
     * @param incomingCount   chapters the upload would leave it with
     * @param deletionAllowed the caller confirmed chapters are being removed
     */
    public static void checkNoChapterLoss(int existingCount, int incomingCount, boolean deletionAllowed) {
        if (incomingCount >= existingCount || deletionAllowed) {
            return;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "story.chapters_would_be_lost",
                "Upload would remove chapters",
                ("Truyện đang có %d chương nhưng tệp tải lên chỉ có %d chương. "
                        + "Thao tác này sẽ xoá mất chương, nên đã bị từ chối. "
                        + "Hãy kiểm tra lại tệp, hoặc xoá từng chương nếu thực sự muốn giảm.")
                        .formatted(existingCount, incomingCount)
        );
    }
}
