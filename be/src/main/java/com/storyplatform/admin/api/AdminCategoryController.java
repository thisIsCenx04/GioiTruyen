package com.storyplatform.admin.api;

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

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminCategoryRow> list() {
        return jdbc.sql("SELECT id, slug, name, description, is_active FROM genres ORDER BY name")
                .query((rs, rowNum) -> new AdminCategoryRow(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("name"),
                        nullToEmpty(rs.getString("description")),
                        0,
                        rs.getBoolean("is_active"),
                        1
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

    private Genre find(UUID id) {
        return genreRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "category.not_found", "Category not found", "Category not found"));
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

    private static AdminCategoryRow toRow(Genre genre) {
        return new AdminCategoryRow(
                genre.getId().toString(),
                genre.getSlug(),
                genre.getName(),
                nullToEmpty(genre.getDescription()),
                0,
                Boolean.TRUE.equals(genre.getIsActive()),
                1
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

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "request.invalid",
                    "Missing field", field + " must not be blank");
        }
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
