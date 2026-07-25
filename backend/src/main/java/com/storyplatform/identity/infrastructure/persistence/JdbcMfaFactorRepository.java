package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port.MfaFactorRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcMfaFactorRepository implements MfaFactorRepository {

    private final JdbcClient jdbc;

    public JdbcMfaFactorRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<Factor> findByUserId(String userId) {
        return jdbc.sql("""
                        SELECT user_id, protected_secret, enabled
                        FROM mfa_factors
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query((result, rowNumber) -> new Factor(
                        result.getString("user_id"),
                        result.getString("protected_secret"),
                        result.getBoolean("enabled")
                ))
                .optional();
    }

    @Override
    public boolean savePending(
            String userId,
            String protectedSecret,
            Instant createdAt
    ) {
        return jdbc.sql("""
                        INSERT INTO mfa_factors (
                            user_id, protected_secret, enabled,
                            created_at, updated_at
                        ) VALUES (
                            :userId, :protectedSecret, FALSE,
                            :createdAt, :createdAt
                        )
                        ON DUPLICATE KEY UPDATE
                            protected_secret =
                                IF(enabled, protected_secret, VALUES(protected_secret)),
                            created_at = IF(enabled, created_at, VALUES(created_at)),
                            updated_at = IF(enabled, updated_at, VALUES(updated_at))
                        """)
                .param("userId", userId)
                .param("protectedSecret", protectedSecret)
                .param("createdAt", createdAt)
                .update() > 0;
    }

    @Override
    @Transactional
    public boolean activate(
            String userId,
            List<String> recoveryCodeHashes,
            Instant activatedAt
    ) {
        int activated = jdbc.sql("""
                        UPDATE mfa_factors
                        SET enabled = TRUE,
                            activated_at = :activatedAt,
                            updated_at = :activatedAt
                        WHERE user_id = :userId AND enabled = FALSE
                        """)
                .param("userId", userId)
                .param("activatedAt", activatedAt)
                .update();
        if (activated == 0) {
            return false;
        }
        recoveryCodeHashes.forEach(codeHash -> jdbc.sql("""
                        INSERT INTO mfa_recovery_codes (
                            user_id, code_hash, created_at
                        ) VALUES (:userId, :codeHash, :createdAt)
                        """)
                .param("userId", userId)
                .param("codeHash", codeHash)
                .param("createdAt", activatedAt)
                .update());
        return true;
    }

    @Override
    public boolean consumeRecoveryCode(
            String userId,
            String recoveryCodeHash,
            Instant consumedAt
    ) {
        return jdbc.sql("""
                        UPDATE mfa_recovery_codes codes
                        JOIN mfa_factors factors
                          ON factors.user_id = codes.user_id
                        SET codes.consumed_at = :consumedAt
                        WHERE codes.user_id = :userId
                          AND codes.code_hash = :codeHash
                          AND codes.consumed_at IS NULL
                          AND factors.enabled = TRUE
                        """)
                .param("consumedAt", consumedAt)
                .param("userId", userId)
                .param("codeHash", recoveryCodeHash)
                .update() == 1;
    }
}
