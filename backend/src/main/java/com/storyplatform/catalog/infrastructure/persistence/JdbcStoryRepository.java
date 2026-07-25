package com.storyplatform.catalog.infrastructure.persistence;

import com.storyplatform.catalog.application.PublicStoryProjection;
import com.storyplatform.catalog.application.port.StoryRepository;
import com.storyplatform.catalog.domain.Story;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcStoryRepository implements StoryRepository {

    private static final String PUBLIC_SELECT = """
            SELECT s.id, s.team_id, s.slug, s.title, s.synopsis,
                   s.origin, s.language, s.completion_status,
                   s.published_at, s.updated_at, s.version,
                   GROUP_CONCAT(sc.category_id ORDER BY sc.category_id)
                       AS category_ids
            FROM stories s
            LEFT JOIN story_categories sc ON sc.story_id = s.id
            """;

    private final JdbcClient jdbc;

    public JdbcStoryRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional
    public boolean insertIfSlugAvailable(Story story) {
        try {
            jdbc.sql("""
                            INSERT INTO stories (
                                id, team_id, slug, title, synopsis,
                                origin, language, completion_status,
                                workflow_status, current_revision,
                                cover_asset_id, published_at,
                                created_at, updated_at, version
                            ) VALUES (
                                :id, :teamId, :slug, :title, :synopsis,
                                :origin, :language, :completionStatus,
                                :workflowStatus, :currentRevision,
                                :coverAssetId, :publishedAt,
                                :createdAt, :updatedAt, :version
                            )
                            """)
                    .param("id", story.id())
                    .param("teamId", story.teamId())
                    .param("slug", story.slug())
                    .param("title", story.title())
                    .param("synopsis", story.synopsis())
                    .param("origin", story.origin().name())
                    .param("language", story.language())
                    .param(
                            "completionStatus",
                            story.completionStatus().name()
                    )
                    .param("workflowStatus", story.workflowStatus().name())
                    .param("currentRevision", story.currentRevision())
                    .param("coverAssetId", story.coverAssetId())
                    .param("publishedAt", story.publishedAt())
                    .param("createdAt", story.createdAt())
                    .param("updatedAt", story.updatedAt())
                    .param("version", story.version())
                    .update();
            story.aliases().forEach(alias -> jdbc.sql("""
                            INSERT INTO story_aliases (story_id, alias)
                            VALUES (:storyId, :alias)
                            """)
                    .param("storyId", story.id())
                    .param("alias", alias)
                    .update());
            story.categoryIds().forEach(categoryId -> jdbc.sql("""
                            INSERT INTO story_categories (
                                story_id, category_id
                            ) VALUES (:storyId, :categoryId)
                            """)
                    .param("storyId", story.id())
                    .param("categoryId", categoryId)
                    .update());
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    @Override
    public Optional<PublicStoryProjection> findPublishedByIdOrSlug(
            String value
    ) {
        return jdbc.sql(PUBLIC_SELECT + """
                         WHERE s.workflow_status = 'PUBLISHED'
                           AND (s.id = :value OR s.slug = :value)
                         GROUP BY s.id
                        """)
                .param("value", value)
                .query(JdbcStoryRepository::mapProjection)
                .optional();
    }

    @Override
    public List<PublicStoryProjection> findPublished(
            StoryListQuery request
    ) {
        String sortColumn = request.sort() == Sort.UPDATED_DESC
                ? "s.updated_at"
                : "s.published_at";
        StringBuilder sql = new StringBuilder(PUBLIC_SELECT)
                .append(" WHERE s.workflow_status = 'PUBLISHED'");
        Map<String, Object> parameters = new HashMap<>();
        if (!request.categoryIds().isEmpty()) {
            sql.append("""
                     AND s.id IN (
                         SELECT filtered.story_id
                         FROM story_categories filtered
                         WHERE filtered.category_id IN (:categoryIds)
                         GROUP BY filtered.story_id
                         HAVING COUNT(DISTINCT filtered.category_id)
                             = :categoryCount
                     )
                    """);
            parameters.put("categoryIds", request.categoryIds());
            parameters.put("categoryCount", request.categoryIds().size());
        }
        if (request.completionStatus() != null) {
            sql.append(" AND s.completion_status = :completionStatus");
            parameters.put(
                    "completionStatus",
                    request.completionStatus().name()
            );
        }
        if (request.origin() != null) {
            sql.append(" AND s.origin = :origin");
            parameters.put("origin", request.origin().name());
        }
        if (request.teamId() != null) {
            sql.append(" AND s.team_id = :teamId");
            parameters.put("teamId", request.teamId());
        }
        if (request.afterValue() != null) {
            sql.append(" AND (")
                    .append(sortColumn)
                    .append(" < :afterValue OR (")
                    .append(sortColumn)
                    .append(" = :afterValue AND s.id < :afterId))");
            parameters.put("afterValue", request.afterValue());
            parameters.put("afterId", request.afterId());
        }
        sql.append(" GROUP BY s.id ORDER BY ")
                .append(sortColumn)
                .append(" DESC, s.id DESC LIMIT :limit");
        parameters.put("limit", request.limit());
        return jdbc.sql(sql.toString())
                .params(parameters)
                .query(JdbcStoryRepository::mapProjection)
                .list();
    }

    private static PublicStoryProjection mapProjection(
            ResultSet result,
            int rowNumber
    ) throws SQLException {
        String categories = result.getString("category_ids");
        List<String> categoryIds = categories == null
                ? List.of()
                : Arrays.asList(categories.split(","));
        return new PublicStoryProjection(
                result.getString("id"),
                result.getString("team_id"),
                result.getString("slug"),
                result.getString("title"),
                result.getString("synopsis"),
                categoryIds,
                Story.Origin.valueOf(result.getString("origin")),
                result.getString("language"),
                Story.CompletionStatus.valueOf(
                        result.getString("completion_status")
                ),
                result.getTimestamp("published_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("version")
        );
    }
}
