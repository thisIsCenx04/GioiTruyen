package com.storyplatform.admin.api;

import static com.storyplatform.admin.application.dto.AdminDtos.timestamp;

import com.storyplatform.admin.application.dto.AdminDtos.AdminCategoryRow;
import com.storyplatform.admin.application.dto.AdminDtos.UpsertCategoryRequest;
import com.storyplatform.catalog.domain.Genre;
import com.storyplatform.catalog.infrastructure.GenreRepository;
import com.storyplatform.shared.api.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/admin/content/categories")
public class AdminCategoryController {

    private final GenreRepository genreRepository;
    private final JdbcClient jdbc;

    public AdminCategoryController(GenreRepository genreRepository, JdbcClient jdbc) {
        this.genreRepository = genreRepository;
        this.jdbc = jdbc;
    }

    /**
     * Every genre, with how many stories carry it.
     *
     * <p>Counted with a correlated subquery rather than a join and GROUP BY, so
     * a genre no story uses still comes back - with a zero, which is exactly
     * the row an admin is looking for when deciding what to retire. The
     * published count is separate because a genre can look busy while every
     * story on it is still a draft.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminCategoryRow> list() {
        return jdbc.sql("""
                        SELECT g.id, g.slug, g.name, g.description, g.is_active,
                               g.created_at, g.updated_at,
                               (SELECT COUNT(*) FROM story_genres sg
                                 WHERE sg.genre_id = g.id) AS story_count,
                               (SELECT COUNT(*) FROM story_genres sg
                                  JOIN stories s ON s.id = sg.story_id
                                 WHERE sg.genre_id = g.id
                                   AND s.status = 'PUBLISHED') AS published_story_count
                        FROM genres g
                        ORDER BY g.name
                        """)
                .query((rs, rowNum) -> new AdminCategoryRow(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("name"),
                        nullToEmpty(rs.getString("description")),
                        rs.getBoolean("is_active"),
                        1,
                        timestamp(rs, "created_at"),
                        timestamp(rs, "updated_at"),
                        rs.getLong("story_count"),
                        rs.getLong("published_story_count")
                ))
                .list();
    }

    @PostMapping
    @Transactional
    public AdminCategoryRow create(@RequestBody UpsertCategoryRequest request) {
        requireText(request.name(), "name");
        String slug = slugOrDerive(request.slug(), request.name());
        requireUniqueSlug(slug, null);

        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        boolean active = request.active() == null || request.active();

        // Ids are assigned here, so an explicit INSERT is used; repository.save()
        // would treat the populated id as an existing row and emit an UPDATE.
        jdbc.sql("""
                        INSERT INTO genres (id, name, slug, description, is_active, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id.toString(), request.name().trim(), slug,
                        nullToEmpty(request.description()), active,
                        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now))
                .update();

        return toRow(find(id));
    }

    @PutMapping("/{id}")
    @Transactional
    public AdminCategoryRow update(@PathVariable UUID id, @RequestBody UpsertCategoryRequest request) {
        Genre genre = find(id);
        requireText(request.name(), "name");
        String slug = slugOrDerive(request.slug(), request.name());
        requireUniqueSlug(slug, id);

        genre.setName(request.name().trim());
        genre.setSlug(slug);
        genre.setDescription(nullToEmpty(request.description()));
        if (request.active() != null) {
            genre.setIsActive(request.active());
        }
        genre.setUpdatedAt(Instant.now());
        return toRow(genreRepository.save(genre));
    }

    /**
     * Genres are referenced by story_genres with ON DELETE RESTRICT, so a genre
     * still in use is deactivated rather than removed.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public void archive(@PathVariable UUID id) {
        Genre genre = find(id);
        long linkedStories = jdbc.sql("SELECT COUNT(*) FROM story_genres WHERE genre_id = ?")
                .param(id.toString())
                .query(Long.class)
                .optional()
                .orElse(0L);

        if (linkedStories > 0) {
            genre.setIsActive(false);
            genre.setUpdatedAt(Instant.now());
            genreRepository.save(genre);
            return;
        }
        genreRepository.delete(genre);
    }

    /**
     * Removes a genre outright. Refused while any story still uses it, since
     * story_genres is ON DELETE RESTRICT and those stories would lose their
     * classification without anyone noticing.
     */
    @DeleteMapping("/{id}/permanent")
    @Transactional
    public void deletePermanently(@PathVariable UUID id) {
        Genre genre = find(id);
        long linkedStories = jdbc.sql("SELECT COUNT(*) FROM story_genres WHERE genre_id = ?")
                .param(id.toString())
                .query(Long.class)
                .optional()
                .orElse(0L);
        if (linkedStories > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "category.in_use",
                    "Category still in use",
                    ("Thể loại \"%s\" đang gắn với %d truyện nên không thể xóa. "
                            + "Hãy gỡ thể loại khỏi các truyện đó, hoặc dùng \"Ẩn thể loại\".")
                            .formatted(genre.getName(), linkedStories));
        }
        jdbc.sql("DELETE FROM genres WHERE id = ?").param(id.toString()).update();
    }

    private Genre find(UUID id) {
        return genreRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "category.not_found", "Category not found", "Category not found"));
    }

    private long countStories(String genreId, boolean publishedOnly) {
        String sql = publishedOnly
                ? """
                  SELECT COUNT(*) FROM story_genres sg
                    JOIN stories s ON s.id = sg.story_id
                   WHERE sg.genre_id = ? AND s.status = 'PUBLISHED'
                  """
                : "SELECT COUNT(*) FROM story_genres WHERE genre_id = ?";
        return jdbc.sql(sql).param(genreId).query(Long.class).optional().orElse(0L);
    }

    private void requireUniqueSlug(String slug, UUID excludedId) {
        long taken = excludedId == null
                ? jdbc.sql("SELECT COUNT(*) FROM genres WHERE slug = ?")
                        .param(slug).query(Long.class).optional().orElse(0L)
                : jdbc.sql("SELECT COUNT(*) FROM genres WHERE slug = ? AND id <> ?")
                        .params(slug, excludedId.toString()).query(Long.class).optional().orElse(0L);
        if (taken > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "category.slug_taken",
                    "Slug already used", "Slug already used by another category");
        }
    }

    /**
     * The row returned after a write. The story counts are read fresh rather
     * than assumed: creating or renaming a genre never moves a story onto or
     * off it, so a newly created genre is genuinely on zero and an edited one
     * keeps whatever it already had.
     */
    private AdminCategoryRow toRow(Genre genre) {
        String id = genre.getId().toString();
        return new AdminCategoryRow(
                id,
                genre.getSlug(),
                genre.getName(),
                nullToEmpty(genre.getDescription()),
                Boolean.TRUE.equals(genre.getIsActive()),
                1,
                genre.getCreatedAt() == null ? null : genre.getCreatedAt().toString(),
                genre.getUpdatedAt() == null ? null : genre.getUpdatedAt().toString(),
                countStories(id, false),
                countStories(id, true)
        );
    }

    static String slugOrDerive(String slug, String fallbackSource) {
        String candidate = slug == null || slug.isBlank() ? fallbackSource : slug;
        String normalized = AdminSlugs.slugify(candidate);
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "category.invalid_slug",
                    "Invalid slug", "slug could not be derived from the provided values");
        }
        return normalized;
    }

    /** Vietnamese labels for the fields an admin actually sees in the drawer. */
    private static final java.util.Map<String, String> FIELD_LABELS = java.util.Map.of(
            "title", "Tên truyện",
            "teamId", "Team đăng truyện",
            "name", "Tên",
            "slug", "Đường dẫn (slug)",
            "email", "Email",
            "displayName", "Tên hiển thị",
            "ownerUserId", "Chủ sở hữu"
    );

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            // The detail reaches the admin's error popup, so it names the field
            // the way the form labels it rather than by its JSON key.
            throw new ApiException(HttpStatus.BAD_REQUEST, "request.invalid",
                    "Missing field",
                    "Chưa nhập " + FIELD_LABELS.getOrDefault(field, field) + ".");
        }
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
