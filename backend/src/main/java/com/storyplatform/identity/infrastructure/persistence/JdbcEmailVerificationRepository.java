package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port.EmailVerificationRepository;
import com.storyplatform.identity.domain.EmailVerification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcEmailVerificationRepository
        implements EmailVerificationRepository {

    private final JdbcClient jdbc;

    public JdbcEmailVerificationRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void save(EmailVerification verification) {
        jdbc.sql("""
                        INSERT INTO email_verification_tokens (
                            id, user_id, token_hash, expires_at,
                            consumed_at, created_at
                        ) VALUES (
                            :id, :userId, :tokenHash, :expiresAt,
                            :consumedAt, :createdAt
                        )
                        """)
                .param("id", verification.id())
                .param("userId", verification.userId())
                .param("tokenHash", verification.tokenHash())
                .param("expiresAt", verification.expiresAt())
                .param("consumedAt", verification.consumedAt())
                .param("createdAt", verification.createdAt())
                .update();
    }

    @Override
    @Transactional
    public Optional<EmailVerification> consume(
            String tokenHash,
            Instant consumedAt
    ) {
        int updated = jdbc.sql("""
                        UPDATE email_verification_tokens
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
                        FROM email_verification_tokens
                        WHERE token_hash = :tokenHash
                        """)
                .param("tokenHash", tokenHash)
                .query((result, rowNumber) -> new EmailVerification(
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
