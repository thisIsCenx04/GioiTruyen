package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port.PasswordResetRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcPasswordResetRepository
        implements PasswordResetRepository {

    private final JdbcClient jdbc;

    public JdbcPasswordResetRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void save(PasswordReset reset) {
        jdbc.sql("""
                        INSERT INTO password_reset_tokens (
                            id, user_id, token_hash, expires_at,
                            consumed_at, created_at
                        ) VALUES (
                            :id, :userId, :tokenHash, :expiresAt,
                            :consumedAt, :createdAt
                        )
                        """)
                .param("id", reset.id())
                .param("userId", reset.userId())
                .param("tokenHash", reset.tokenHash())
                .param("expiresAt", reset.expiresAt())
                .param("consumedAt", reset.consumedAt())
                .param("createdAt", reset.createdAt())
                .update();
    }

    @Override
    @Transactional
    public Optional<PasswordReset> consume(
            String tokenHash,
            Instant consumedAt
    ) {
        int updated = jdbc.sql("""
                        UPDATE password_reset_tokens
                        SET consumed_at = :consumedAt
                        WHERE token_hash = :tokenHash
                          AND consumed_at IS NULL
                          AND expires_at > :consumedAt
                        """)
                .param("consumedAt", consumedAt)
                .param("tokenHash", tokenHash)
                .update();
        if (updated == 0) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        SELECT id, user_id, token_hash, expires_at,
                               consumed_at, created_at
                        FROM password_reset_tokens
                        WHERE token_hash = :tokenHash
                        """)
                .param("tokenHash", tokenHash)
                .query((result, rowNumber) -> new PasswordReset(
                        result.getString("id"),
                        result.getString("user_id"),
                        result.getString("token_hash"),
                        result.getTimestamp("expires_at").toInstant(),
                        result.getTimestamp("consumed_at").toInstant(),
                        result.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }
}
