package com.storyplatform.catalog.infrastructure.persistence;

import com.storyplatform.catalog.application.PublicChapterProjection;
import com.storyplatform.catalog.application.port.ChapterRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcChapterRepository implements ChapterRepository {

    private static final String PUBLIC_SELECT = """
            SELECT id, story_id, chapter_number, slug, title,
                   published_at, version
            FROM chapters
            """;

    private final JdbcClient jdbc;

    public JdbcChapterRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<PublicChapterProjection> findPublished(
            ChapterListQuery request
    ) {
        StringBuilder sql = new StringBuilder(PUBLIC_SELECT)
                .append("""
                         WHERE story_id = :storyId
                           AND workflow_status = 'PUBLISHED'
                        """);
        if (request.afterNumber() != null) {
            sql.append("""
                     AND (
                         chapter_number > :afterNumber
                         OR (
                             chapter_number = :afterNumber
                             AND id > :afterId
                         )
                     )
                    """);
        }
        sql.append("""
                 ORDER BY chapter_number ASC, id ASC
                 LIMIT :limit
                """);
        JdbcClient.StatementSpec statement = jdbc.sql(sql.toString())
                .param("storyId", request.storyId())
                .param("limit", request.limit());
        if (request.afterNumber() != null) {
            statement = statement
                    .param("afterNumber", request.afterNumber())
                    .param("afterId", request.afterId());
        }
        return statement.query(
                JdbcChapterRepository::mapProjection
        ).list();
    }

    @Override
    public Optional<StoredChapter> findPublishedDetail(String chapterId) {
        return jdbc.sql("""
                        SELECT c.id, c.story_id, c.chapter_number, c.slug,
                               c.title, c.published_at, c.version,
                               r.id AS revision_id, r.revision_no,
                               r.content_html, r.plain_text, r.checksum
                        FROM chapters c
                        JOIN chapter_revisions r
                          ON r.id = c.current_revision
                         AND r.chapter_id = c.id
                        WHERE c.id = :chapterId
                          AND c.workflow_status = 'PUBLISHED'
                        """)
                .param("chapterId", chapterId)
                .query((result, rowNumber) -> new StoredChapter(
                        mapProjection(result, rowNumber),
                        result.getString("revision_id"),
                        result.getLong("revision_no"),
                        result.getString("content_html"),
                        result.getString("plain_text"),
                        result.getString("checksum")
                ))
                .optional();
    }

    @Override
    public Optional<PublicChapterProjection> previous(
            String storyId,
            int number,
            String chapterId
    ) {
        return neighbor(storyId, number, chapterId, false);
    }

    @Override
    public Optional<PublicChapterProjection> next(
            String storyId,
            int number,
            String chapterId
    ) {
        return neighbor(storyId, number, chapterId, true);
    }

    private Optional<PublicChapterProjection> neighbor(
            String storyId,
            int number,
            String chapterId,
            boolean next
    ) {
        String comparison = next ? ">" : "<";
        String direction = next ? "ASC" : "DESC";
        return jdbc.sql(PUBLIC_SELECT + """
                         WHERE story_id = :storyId
                           AND workflow_status = 'PUBLISHED'
                           AND (
                               chapter_number %s :number
                               OR (
                                   chapter_number = :number
                                   AND id %s :chapterId
                               )
                           )
                         ORDER BY chapter_number %s, id %s
                         LIMIT 1
                        """.formatted(
                                comparison,
                                comparison,
                                direction,
                                direction
                        ))
                .param("storyId", storyId)
                .param("number", number)
                .param("chapterId", chapterId)
                .query(JdbcChapterRepository::mapProjection)
                .optional();
    }

    private static PublicChapterProjection mapProjection(
            ResultSet result,
            int rowNumber
    ) throws SQLException {
        return new PublicChapterProjection(
                result.getString("id"),
                result.getString("story_id"),
                result.getInt("chapter_number"),
                result.getString("slug"),
                result.getString("title"),
                result.getTimestamp("published_at").toInstant(),
                result.getLong("version")
        );
    }
}
