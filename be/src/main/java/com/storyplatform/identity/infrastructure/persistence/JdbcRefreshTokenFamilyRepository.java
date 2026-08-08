package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.port.RefreshTokenFamilyRepository;
import com.storyplatform.identity.domain.RefreshTokenFamily;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcRefreshTokenFamilyRepository
        implements RefreshTokenFamilyRepository {

    private static final String REUSE_REASON = "REUSE_DETECTED";

    private final JdbcClient jdbc;

    public JdbcRefreshTokenFamilyRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional
    public void create(RefreshTokenFamily family) {
        jdbc.sql("""
                        INSERT INTO refresh_token_families (
                            id, user_id, security_version, generation,
                            current_token_hash, created_at, updated_at,
                            expires_at, revoked_at, revoke_reason, version
                        ) VALUES (
                            :id, :userId, :securityVersion, :generation,
                            :currentTokenHash, :createdAt, :updatedAt,
                            :expiresAt, :revokedAt, :revokeReason, 0
                        )
                        """)
                .param("id", family.id())
                .param("userId", family.userId())
                .param("securityVersion", family.securityVersion())
                .param("generation", family.generation())
                .param("currentTokenHash", family.currentTokenHash())
                .param("createdAt", family.createdAt())
                .param("updatedAt", family.updatedAt())
                .param("expiresAt", family.expiresAt())
                .param("revokedAt", family.revokedAt())
                .param("revokeReason", family.revokeReason())
                .update();
        family.usedTokenHashes().forEach(hash ->
                insertHistory(family.id(), hash, family.updatedAt()));
    }

    @Override
    @Transactional
    public RotationResult rotate(
            String currentTokenHash,
            String nextTokenHash,
            Instant now,
            int maximumGeneration
    ) {
        Optional<FamilyState> active = jdbc.sql("""
                        SELECT id, user_id, security_version, generation
                        FROM refresh_token_families
                        WHERE current_token_hash = :tokenHash
                          AND revoked_at IS NULL
                          AND expires_at > :now
                        FOR UPDATE
                        """)
                .param("tokenHash", currentTokenHash)
                .param("now", now)
                .query(JdbcRefreshTokenFamilyRepository::mapState)
                .optional();
        if (active.isPresent()) {
            FamilyState family = active.orElseThrow();
            if (family.generation() >= maximumGeneration) {
                return RotationResult.invalid();
            }
            insertHistory(family.id(), currentTokenHash, now);
            jdbc.sql("""
                            UPDATE refresh_token_families
                            SET current_token_hash = :nextTokenHash,
                                generation = generation + 1,
                                updated_at = :now,
                                version = version + 1
                            WHERE id = :id
                            """)
                    .param("nextTokenHash", nextTokenHash)
                    .param("now", now)
                    .param("id", family.id())
                    .update();
            return RotationResult.rotated(
                    family.id(),
                    family.userId(),
                    family.securityVersion()
            );
        }

        Optional<String> replayedFamily = jdbc.sql("""
                        SELECT family_id
                        FROM refresh_token_history
                        WHERE token_hash = :tokenHash
                        FOR UPDATE
                        """)
                .param("tokenHash", currentTokenHash)
                .query(String.class)
                .optional();
        if (replayedFamily.isEmpty()) {
            return RotationResult.invalid();
        }
        String familyId = replayedFamily.orElseThrow();
        int revoked = jdbc.sql("""
                        UPDATE refresh_token_families
                        SET revoked_at = :now,
                            revoke_reason = :reason,
                            updated_at = :now,
                            version = version + 1
                        WHERE id = :id AND revoked_at IS NULL
                        """)
                .param("now", now)
                .param("reason", REUSE_REASON)
                .param("id", familyId)
                .update();
        return revoked == 1
                ? RotationResult.reuseDetected(familyId)
                : RotationResult.invalid();
    }

    @Override
    public void revoke(
            String familyId,
            Instant revokedAt,
            String reason
    ) {
        revoke(familyId, null, revokedAt, reason);
    }

    @Override
    public boolean revokeOwned(
            String familyId,
            String userId,
            Instant revokedAt,
            String reason
    ) {
        return revoke(familyId, userId, revokedAt, reason) == 1;
    }

    @Override
    public long revokeAllOwned(
            String userId,
            Instant revokedAt,
            String reason
    ) {
        return jdbc.sql("""
                        UPDATE refresh_token_families
                        SET revoked_at = :revokedAt,
                            revoke_reason = :reason,
                            updated_at = :revokedAt,
                            version = version + 1
                        WHERE user_id = :userId AND revoked_at IS NULL
                        """)
                .param("revokedAt", revokedAt)
                .param("reason", reason)
                .param("userId", userId)
                .update();
    }

    @Override
    public List<SessionRecord> findActiveByUser(
            String userId,
            Instant now
    ) {
        return jdbc.sql("""
                        SELECT id, created_at, updated_at, expires_at
                        FROM refresh_token_families
                        WHERE user_id = :userId
                          AND revoked_at IS NULL
                          AND expires_at > :now
                        ORDER BY created_at DESC
                        """)
                .param("userId", userId)
                .param("now", now)
                .query((result, rowNumber) -> new SessionRecord(
                        result.getString("id"),
                        instant(result, "created_at"),
                        instant(result, "updated_at"),
                        instant(result, "expires_at")
                ))
                .list();
    }

    @Override
    public boolean isActiveOwned(
            String familyId,
            String userId,
            Instant now
    ) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM refresh_token_families
                        WHERE id = :familyId
                          AND user_id = :userId
                          AND revoked_at IS NULL
                          AND expires_at > :now
                        """)
                .param("familyId", familyId)
                .param("userId", userId)
                .param("now", now)
                .query(Long.class)
                .single() == 1;
    }

    private int revoke(
            String familyId,
            String userId,
            Instant revokedAt,
            String reason
    ) {
        String ownerClause = userId == null ? "" : " AND user_id = :userId";
        JdbcClient.StatementSpec statement = jdbc.sql("""
                        UPDATE refresh_token_families
                        SET revoked_at = :revokedAt,
                            revoke_reason = :reason,
                            updated_at = :revokedAt,
                            version = version + 1
                        WHERE id = :familyId AND revoked_at IS NULL
                        """ + ownerClause)
                .param("familyId", familyId)
                .param("revokedAt", revokedAt)
                .param("reason", reason);
        if (userId != null) {
            statement = statement.param("userId", userId);
        }
        return statement.update();
    }

    private void insertHistory(
            String familyId,
            String tokenHash,
            Instant usedAt
    ) {
        jdbc.sql("""
                        INSERT INTO refresh_token_history (
                            family_id, token_hash, used_at
                        ) VALUES (:familyId, :tokenHash, :usedAt)
                        """)
                .param("familyId", familyId)
                .param("tokenHash", tokenHash)
                .param("usedAt", usedAt)
                .update();
    }

    private static FamilyState mapState(
            ResultSet result,
            int rowNumber
    ) throws SQLException {
        return new FamilyState(
                result.getString("id"),
                result.getString("user_id"),
                result.getLong("security_version"),
                result.getInt("generation")
        );
    }

    private static Instant instant(
            ResultSet result,
            String column
    ) throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    private record FamilyState(
            String id,
            String userId,
            long securityVersion,
            int generation
    ) {
    }
}
