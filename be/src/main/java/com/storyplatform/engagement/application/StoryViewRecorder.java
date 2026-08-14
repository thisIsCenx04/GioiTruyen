package com.storyplatform.engagement.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts a story view when a reader opens one of its chapters.
 *
 * <p>The tables, entities and repositories for this existed from the start but
 * nothing ever wrote to them, so every story published after launch sat at zero
 * views for good. This is the missing write path.
 *
 * <p>A view is recorded per chapter opened, not per page load: the same reader
 * returning to the same chapter within {@link #DEDUPE_WINDOW_MINUTES} minutes is
 * one view, so a refresh, a re-read after the paywall, or a flaky connection
 * retrying does not inflate the number. Different chapters of the same story
 * each count, which is what makes the figure track reading rather than clicks.
 */
@Service
public class StoryViewRecorder {

    private static final Logger log = LoggerFactory.getLogger(StoryViewRecorder.class);

    /** How long the same reader re-opening the same chapter stays one view. */
    private static final int DEDUPE_WINDOW_MINUTES = 30;

    private final JdbcClient jdbc;

    public StoryViewRecorder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records that {@code chapterId} of {@code storyId} was read.
     *
     * <p>Runs in its own transaction and swallows its own failures: a view is
     * a statistic, and losing one must never turn a readable chapter into an
     * error page for the reader who triggered it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChapterView(String storyId, String chapterId, String readerId, String clientIp) {
        if (storyId == null || chapterId == null) {
            return;
        }
        try {
            // Signed-in readers are identified by account, so the count follows
            // them across devices; guests fall back to a hashed address, which
            // is enough to spot a refresh without storing the address itself.
            String viewerKey = readerId != null && !readerId.isBlank()
                    ? "u:" + readerId
                    : "ip:" + hash(clientIp);

            boolean seenRecently = jdbc.sql("""
                            SELECT COUNT(*) FROM story_views
                            WHERE chapter_id = :chapterId
                              AND session_id = :viewerKey
                              AND viewed_at > NOW() - INTERVAL :minutes MINUTE
                            """)
                    .param("chapterId", chapterId)
                    .param("viewerKey", viewerKey)
                    .param("minutes", DEDUPE_WINDOW_MINUTES)
                    .query(Long.class)
                    .single() > 0;
            if (seenRecently) {
                return;
            }

            jdbc.sql("""
                            INSERT INTO story_views (id, story_id, chapter_id, user_id, session_id, ip_hash, viewed_at)
                            VALUES (:id, :storyId, :chapterId, :userId, :viewerKey, :ipHash, NOW())
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("storyId", storyId)
                    .param("chapterId", chapterId)
                    .param("userId", readerId == null || readerId.isBlank() ? null : readerId)
                    .param("viewerKey", viewerKey)
                    .param("ipHash", hash(clientIp))
                    .update();

            // Denormalised so the shelves and rankings can sort without
            // counting rows in story_views on every request.
            jdbc.sql("UPDATE stories SET view_count_cache = view_count_cache + 1 WHERE id = :storyId")
                    .param("storyId", storyId)
                    .update();

            // The reader is new to this chapter, but may already have read
            // another one today, so only "views" is unconditional.
            jdbc.sql("""
                            INSERT INTO story_daily_stats (story_id, stat_date, views, unique_views)
                            VALUES (:storyId, CURDATE(), 1, :uniqueToday)
                            ON DUPLICATE KEY UPDATE
                                views = views + 1,
                                unique_views = unique_views + VALUES(unique_views)
                            """)
                    .param("storyId", storyId)
                    .param("uniqueToday", firstViewOfStoryToday(storyId, viewerKey) ? 1 : 0)
                    .update();
        } catch (RuntimeException exception) {
            log.warn("Could not record view for story {} chapter {}: {}",
                    storyId, chapterId, exception.getMessage());
        }
    }

    /**
     * Whether this viewer had not been seen on this story earlier today.
     *
     * <p>Called after the row above is inserted, so the reader's own view is
     * excluded by looking for any other row.
     */
    private boolean firstViewOfStoryToday(String storyId, String viewerKey) {
        Long earlier = jdbc.sql("""
                        SELECT COUNT(*) FROM story_views
                        WHERE story_id = :storyId
                          AND session_id = :viewerKey
                          AND DATE(viewed_at) = CURDATE()
                        """)
                .param("storyId", storyId)
                .param("viewerKey", viewerKey)
                .query(Long.class)
                .single();
        return earlier != null && earlier <= 1;
    }

    /** Addresses are hashed, never stored: the count needs identity, not the address. */
    private static String hash(String value) {
        String source = value == null || value.isBlank() ? "unknown" : value;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
