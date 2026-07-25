package com.storyplatform.reading.infrastructure.persistence;

import com.storyplatform.reading.application.port.ReadingCompletionRepository;
import com.storyplatform.reading.application.port.ReadingHeartbeatRepository;
import com.storyplatform.reading.application.port.ReadingSessionRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public class JdbcReadingSessionRepository
        implements ReadingSessionRepository,
        ReadingHeartbeatRepository,
        ReadingCompletionRepository {

    private final JdbcClient jdbc;

    public JdbcReadingSessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean chapterIsPublished(
            String storyId,
            String chapterId
    ) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM chapters chapter
                        JOIN chapter_revisions revision
                          ON revision.id = chapter.current_revision
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
    public void create(SessionRecord session) {
        jdbc.sql("""
                        INSERT INTO reading_sessions (
                            id, story_id, chapter_id, actor_type, actor_ref,
                            started_at, expires_at, purge_at, status,
                            last_sequence, updated_at
                        ) VALUES (
                            :id, :storyId, :chapterId, :actorType, :actorRef,
                            :startedAt, :expiresAt, :purgeAt, 'ACTIVE',
                            0, :startedAt
                        )
                        """)
                .param("id", session.id())
                .param("storyId", session.storyId())
                .param("chapterId", session.chapterId())
                .param("actorType", session.actorType())
                .param("actorRef", session.actorRef())
                .param("startedAt", session.startedAt())
                .param("expiresAt", session.expiresAt())
                .param("purgeAt", session.purgeAt())
                .update();
    }

    @Override
    @Transactional
    public ApplyResult apply(
            String sessionId,
            String actorRef,
            String batchId,
            long expectedPreviousSequence,
            long lastSequence,
            Instant now
    ) {
        int applied = jdbc.sql("""
                        UPDATE reading_sessions session
                        SET last_sequence = :lastSequence,
                            updated_at = :now
                        WHERE session.id = :sessionId
                          AND session.actor_ref = :actorRef
                          AND session.status = 'ACTIVE'
                          AND session.expires_at > :now
                          AND session.last_sequence = :previousSequence
                          AND NOT EXISTS (
                              SELECT 1
                              FROM reading_session_batches batch
                              WHERE batch.session_id = session.id
                                AND batch.batch_id = :batchId
                          )
                        """)
                .param("lastSequence", lastSequence)
                .param("now", now)
                .param("sessionId", sessionId)
                .param("actorRef", actorRef)
                .param("previousSequence", expectedPreviousSequence)
                .param("batchId", batchId)
                .update();
        if (applied == 1) {
            jdbc.sql("""
                            INSERT INTO reading_session_batches (
                                session_id, batch_id, processed_at
                            ) VALUES (:sessionId, :batchId, :processedAt)
                            """)
                    .param("sessionId", sessionId)
                    .param("batchId", batchId)
                    .param("processedAt", now)
                    .update();
            return ApplyResult.APPLIED;
        }
        Optional<SessionState> state = state(sessionId);
        if (state.isEmpty()
                || !actorRef.equals(state.orElseThrow().actorRef())
                || !"ACTIVE".equals(state.orElseThrow().status())
                || !now.isBefore(state.orElseThrow().expiresAt())) {
            return ApplyResult.NOT_ACTIVE;
        }
        boolean duplicate = jdbc.sql("""
                        SELECT COUNT(*)
                        FROM reading_session_batches
                        WHERE session_id = :sessionId
                          AND batch_id = :batchId
                        """)
                .param("sessionId", sessionId)
                .param("batchId", batchId)
                .query(Long.class)
                .single() == 1;
        return duplicate
                ? ApplyResult.DUPLICATE
                : ApplyResult.SEQUENCE_CONFLICT;
    }

    @Override
    public CompleteResult complete(
            String sessionId,
            String actorRef,
            String completionId,
            long finalSequence,
            Instant completedAt,
            Instant now
    ) {
        int completed = jdbc.sql("""
                        UPDATE reading_sessions
                        SET status = 'COMPLETION_PENDING',
                            completion_id = :completionId,
                            completed_at = :completedAt,
                            updated_at = :now
                        WHERE id = :sessionId
                          AND actor_ref = :actorRef
                          AND status = 'ACTIVE'
                          AND expires_at > :now
                          AND last_sequence = :finalSequence
                          AND (
                              completion_id IS NULL
                              OR completion_id <> :completionId
                          )
                        """)
                .param("completionId", completionId)
                .param("completedAt", completedAt)
                .param("now", now)
                .param("sessionId", sessionId)
                .param("actorRef", actorRef)
                .param("finalSequence", finalSequence)
                .update();
        if (completed == 1) {
            return CompleteResult.APPLIED;
        }
        Optional<SessionState> current = state(sessionId);
        if (current.isPresent()
                && actorRef.equals(current.orElseThrow().actorRef())
                && completionId.equals(
                        current.orElseThrow().completionId()
                )
                && "COMPLETION_PENDING".equals(
                        current.orElseThrow().status()
                )) {
            return CompleteResult.DUPLICATE;
        }
        if (current.isPresent()
                && actorRef.equals(current.orElseThrow().actorRef())
                && "ACTIVE".equals(current.orElseThrow().status())
                && now.isBefore(current.orElseThrow().expiresAt())) {
            return CompleteResult.SEQUENCE_CONFLICT;
        }
        return CompleteResult.NOT_ACTIVE;
    }

    private Optional<SessionState> state(String sessionId) {
        return jdbc.sql("""
                        SELECT actor_ref, status, expires_at, completion_id
                        FROM reading_sessions
                        WHERE id = :sessionId
                        """)
                .param("sessionId", sessionId)
                .query((result, rowNumber) -> new SessionState(
                        result.getString("actor_ref"),
                        result.getString("status"),
                        result.getTimestamp("expires_at").toInstant(),
                        result.getString("completion_id")
                ))
                .optional();
    }

    private record SessionState(
            String actorRef,
            String status,
            Instant expiresAt,
            String completionId
    ) {
    }
}
