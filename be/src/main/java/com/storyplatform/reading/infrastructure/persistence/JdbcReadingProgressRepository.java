package com.storyplatform.reading.infrastructure.persistence;

import com.storyplatform.reading.application.ReadingProgressOperations;
import com.storyplatform.reading.application.port.ReadingHistoryRepository;
import com.storyplatform.reading.application.port.ReadingProgressRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class JdbcReadingProgressRepository
        implements ReadingProgressRepository, ReadingHistoryRepository {

    private static final String SELECT_PROGRESS = """
            SELECT user_id, story_id, chapter_id, position,
                   device_updated_at, updated_at, version
            FROM reading_progress
            """;

    private final JdbcClient jdbc;

    public JdbcReadingProgressRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StoredProgress> find(String userId, String storyId) {
        return jdbc.sql(SELECT_PROGRESS + """
                         WHERE user_id = :userId AND story_id = :storyId
                        """)
                .param("userId", userId)
                .param("storyId", storyId)
                .query(JdbcReadingProgressRepository::stored)
                .optional();
    }

    @Override
    public boolean chapterIsPublished(
            String storyId,
            String chapterId
    ) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM chapters chapter
                        JOIN stories story ON story.id = chapter.story_id
                        WHERE chapter.id = :chapterId
                          AND chapter.story_id = :storyId
                          AND chapter.workflow_status = 'PUBLISHED'
                          AND story.workflow_status = 'PUBLISHED'
                        """)
                .param("chapterId", chapterId)
                .param("storyId", storyId)
                .query(Long.class)
                .single() == 1;
    }

    @Override
    public boolean create(
            String userId,
            ReadingProgressOperations.ProgressView progress
    ) {
        try {
            return jdbc.sql("""
                            INSERT INTO reading_progress (
                                user_id, story_id, chapter_id, position,
                                device_updated_at, updated_at, version
                            ) VALUES (
                                :userId, :storyId, :chapterId, :position,
                                :deviceUpdatedAt, :updatedAt, :version
                            )
                            """)
                    .param("userId", userId)
                    .param("storyId", progress.storyId())
                    .param("chapterId", progress.chapterId())
                    .param("position", progress.position())
                    .param("deviceUpdatedAt", progress.deviceUpdatedAt())
                    .param("updatedAt", progress.updatedAt())
                    .param("version", progress.version())
                    .update() == 1;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    @Override
    public boolean update(
            String userId,
            ReadingProgressOperations.ProgressView progress,
            long expectedVersion,
            Instant previousDeviceUpdatedAt
    ) {
        return jdbc.sql("""
                        UPDATE reading_progress
                        SET chapter_id = :chapterId,
                            position = :position,
                            device_updated_at = :deviceUpdatedAt,
                            updated_at = :updatedAt,
                            version = :nextVersion
                        WHERE user_id = :userId
                          AND story_id = :storyId
                          AND version = :expectedVersion
                          AND device_updated_at = :previousDeviceUpdatedAt
                        """)
                .param("chapterId", progress.chapterId())
                .param("position", progress.position())
                .param("deviceUpdatedAt", progress.deviceUpdatedAt())
                .param("updatedAt", progress.updatedAt())
                .param("nextVersion", progress.version())
                .param("userId", userId)
                .param("storyId", progress.storyId())
                .param("expectedVersion", expectedVersion)
                .param("previousDeviceUpdatedAt", previousDeviceUpdatedAt)
                .update() == 1;
    }

    @Override
    public List<ReadingProgressOperations.ProgressView> list(
            String userId,
            Instant beforeUpdatedAt,
            String beforeStoryId,
            int limit
    ) {
        StringBuilder sql = new StringBuilder(SELECT_PROGRESS)
                .append(" WHERE user_id = :userId");
        if (beforeUpdatedAt != null) {
            sql.append("""
                     AND (
                         updated_at < :beforeUpdatedAt
                         OR (
                             updated_at = :beforeUpdatedAt
                             AND story_id < :beforeStoryId
                         )
                     )
                    """);
        }
        sql.append("""
                 ORDER BY updated_at DESC, story_id DESC
                 LIMIT :limit
                """);
        JdbcClient.StatementSpec statement = jdbc.sql(sql.toString())
                .param("userId", userId)
                .param("limit", limit);
        if (beforeUpdatedAt != null) {
            statement = statement
                    .param("beforeUpdatedAt", beforeUpdatedAt)
                    .param("beforeStoryId", beforeStoryId);
        }
        return statement
                .query(JdbcReadingProgressRepository::stored)
                .list()
                .stream()
                .map(StoredProgress::progress)
                .toList();
    }

    @Override
    public void delete(String userId, String storyId) {
        jdbc.sql("""
                        DELETE FROM reading_progress
                        WHERE user_id = :userId AND story_id = :storyId
                        """)
                .param("userId", userId)
                .param("storyId", storyId)
                .update();
    }

    private static StoredProgress stored(
            ResultSet result,
            int rowNumber
    ) throws SQLException {
        return new StoredProgress(
                result.getString("user_id"),
                new ReadingProgressOperations.ProgressView(
                        result.getString("story_id"),
                        result.getString("chapter_id"),
                        result.getDouble("position"),
                        result.getTimestamp("device_updated_at").toInstant(),
                        result.getTimestamp("updated_at").toInstant(),
                        result.getLong("version")
                )
        );
    }
}
