package com.storyplatform.shared.events.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public class OutboxMessageStore {

    private static final TypeReference<Map<String, String>> TRACE_CONTEXT =
            new TypeReference<>() {
            };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public OutboxMessageStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public Optional<OutboxMessage> claim(
            String owner,
            Instant now,
            Duration leaseDuration
    ) {
        Optional<String> messageId = jdbc.sql("""
                        SELECT id
                        FROM outbox_messages
                        WHERE status IN ('PENDING', 'PROCESSING')
                          AND next_attempt_at <= :now
                          AND (
                              lease_until IS NULL
                              OR lease_until <= :now
                          )
                        ORDER BY next_attempt_at, id
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("now", now)
                .query(String.class)
                .optional();
        if (messageId.isEmpty()) {
            return Optional.empty();
        }
        String id = messageId.orElseThrow();
        jdbc.sql("""
                        UPDATE outbox_messages
                        SET status = 'PROCESSING',
                            lease_owner = :owner,
                            lease_until = :leaseUntil,
                            attempts = attempts + 1
                        WHERE id = :id
                        """)
                .param("owner", owner)
                .param("leaseUntil", now.plus(leaseDuration))
                .param("id", id)
                .update();
        return findById(id);
    }

    public void complete(String id, String owner, Instant processedAt) {
        updateOwned(
                id,
                owner,
                """
                status = 'PROCESSED',
                processed_at = :changedAt,
                lease_owner = NULL,
                lease_until = NULL,
                last_error = NULL
                """,
                processedAt,
                null
        );
    }

    public void scheduleRetry(
            String id,
            String owner,
            Instant nextAttemptAt,
            String errorCode
    ) {
        updateOwned(
                id,
                owner,
                """
                status = 'PENDING',
                next_attempt_at = :changedAt,
                last_error = :errorCode,
                lease_owner = NULL,
                lease_until = NULL
                """,
                nextAttemptAt,
                errorCode
        );
    }

    public void deadLetter(
            String id,
            String owner,
            Instant failedAt,
            String errorCode
    ) {
        updateOwned(
                id,
                owner,
                """
                status = 'DEAD_LETTER',
                processed_at = :changedAt,
                last_error = :errorCode,
                lease_owner = NULL,
                lease_until = NULL
                """,
                failedAt,
                errorCode
        );
    }

    private Optional<OutboxMessage> findById(String id) {
        return jdbc.sql("""
                        SELECT *
                        FROM outbox_messages
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(this::mapMessage)
                .optional();
    }

    private void updateOwned(
            String id,
            String owner,
            String assignments,
            Instant changedAt,
            String errorCode
    ) {
        JdbcClient.StatementSpec statement = jdbc.sql("""
                        UPDATE outbox_messages
                        SET %s
                        WHERE id = :id
                          AND status = 'PROCESSING'
                          AND lease_owner = :owner
                        """.formatted(assignments))
                .param("changedAt", changedAt)
                .param("id", id)
                .param("owner", owner);
        if (errorCode != null) {
            statement = statement.param("errorCode", errorCode);
        }
        if (statement.update() != 1) {
            throw new IllegalStateException(
                    "Outbox message lease was lost"
            );
        }
    }

    private OutboxMessage mapMessage(
            ResultSet result,
            int rowNumber
    ) throws SQLException {
        return new OutboxMessage(
                result.getString("id"),
                result.getString("event_type"),
                result.getInt("event_version"),
                instant(result, "occurred_at"),
                result.getString("correlation_id"),
                traceContext(result.getString("trace_context")),
                result.getString("aggregate_type"),
                result.getString("aggregate_id"),
                result.getString("actor_id"),
                result.getString("team_id"),
                result.getString("content_type"),
                result.getString("payload"),
                OutboxStatus.valueOf(result.getString("status")),
                result.getInt("attempts"),
                instant(result, "next_attempt_at"),
                result.getString("lease_owner"),
                nullableInstant(result, "lease_until"),
                result.getString("last_error"),
                instant(result, "created_at"),
                nullableInstant(result, "processed_at")
        );
    }

    private Map<String, String> traceContext(String value)
            throws SQLException {
        try {
            return json.readValue(value, TRACE_CONTEXT);
        } catch (Exception exception) {
            throw new SQLException("Invalid outbox trace context", exception);
        }
    }

    private static Instant instant(ResultSet result, String column)
            throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(
            ResultSet result,
            String column
    ) throws SQLException {
        var value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
