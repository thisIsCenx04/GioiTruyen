package com.storyplatform.engagement.application;

import com.storyplatform.shared.api.ApiException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two things a reader can do with a story besides read it: keep it
 * ("yêu thích", which fills their library) and follow it for new chapters.
 *
 * <p>Both tables existed from the start with a counter column on {@code stories}
 * beside them, but nothing ever wrote to either, so the hearts and bookmarks on
 * the story page had nowhere to send a click.
 */
@Service
public class StoryRelationService {

    /** What the story page sends as the relation name, and where each is kept. */
    private enum Relation {
        FAVOURITE("library_items", "favorite_count_cache"),
        FOLLOW("story_follows", "follow_count_cache");

        private final String table;
        private final String counterColumn;

        Relation(String table, String counterColumn) {
            this.table = table;
            this.counterColumn = counterColumn;
        }
    }

    public record RelationState(boolean active, long count) {
    }

    private final JdbcClient jdbc;

    public StoryRelationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public RelationState status(String storyId, String relation, String userId) {
        Relation target = parse(relation);
        requireStory(storyId);
        return new RelationState(isActive(target, storyId, userId), count(target, storyId));
    }

    @Transactional
    public RelationState add(String storyId, String relation, String userId) {
        Relation target = parse(relation);
        requireStory(storyId);
        // Idempotent: a double click, or a retry after a dropped response, must
        // not add a second row or count the story twice.
        int inserted = jdbc.sql("""
                        INSERT IGNORE INTO %s (user_id, story_id, created_at)
                        VALUES (:userId, :storyId, NOW())
                        """.formatted(target.table))
                .param("userId", userId)
                .param("storyId", storyId)
                .update();
        if (inserted > 0) {
            adjustCounter(target, storyId, 1);
        }
        return new RelationState(true, count(target, storyId));
    }

    @Transactional
    public RelationState remove(String storyId, String relation, String userId) {
        Relation target = parse(relation);
        requireStory(storyId);
        int deleted = jdbc.sql("""
                        DELETE FROM %s WHERE user_id = :userId AND story_id = :storyId
                        """.formatted(target.table))
                .param("userId", userId)
                .param("storyId", storyId)
                .update();
        if (deleted > 0) {
            adjustCounter(target, storyId, -1);
        }
        return new RelationState(false, count(target, storyId));
    }

    private boolean isActive(Relation target, String storyId, String userId) {
        if (userId == null) {
            return false;
        }
        Long rows = jdbc.sql("""
                        SELECT COUNT(*) FROM %s WHERE user_id = :userId AND story_id = :storyId
                        """.formatted(target.table))
                .param("userId", userId)
                .param("storyId", storyId)
                .query(Long.class)
                .single();
        return rows != null && rows > 0;
    }

    /**
     * Counted from the rows rather than read from the cache column.
     *
     * <p>The cached counters were never maintained before this service existed,
     * so every story carries whatever the seed left there. Reading the truth
     * keeps the number shown to readers honest while the cache catches up.
     */
    private long count(Relation target, String storyId) {
        Long rows = jdbc.sql("SELECT COUNT(*) FROM %s WHERE story_id = :storyId".formatted(target.table))
                .param("storyId", storyId)
                .query(Long.class)
                .single();
        return rows == null ? 0 : rows;
    }

    /** Kept in step for the shelves, which sort on the cached column. */
    private void adjustCounter(Relation target, String storyId, int delta) {
        jdbc.sql("""
                        UPDATE stories
                        SET %1$s = GREATEST(0, CAST(%1$s AS SIGNED) + :delta)
                        WHERE id = :storyId
                        """.formatted(target.counterColumn))
                .param("delta", delta)
                .param("storyId", storyId)
                .update();
    }

    private void requireStory(String storyId) {
        Long rows = jdbc.sql("SELECT COUNT(*) FROM stories WHERE id = :storyId")
                .param("storyId", storyId)
                .query(Long.class)
                .single();
        if (rows == null || rows == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "story.not_found",
                    "Story not found", "Không tìm thấy truyện này.");
        }
    }

    private Relation parse(String relation) {
        String value = relation == null ? "" : relation.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "favorite", "favourite" -> Relation.FAVOURITE;
            case "follow" -> Relation.FOLLOW;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "story.relation_unknown",
                    "Unknown relation", "Chỉ hỗ trợ \"favorite\" và \"follow\".");
        };
    }
}
