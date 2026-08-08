package com.storyplatform.identity.infrastructure.persistence;

import com.storyplatform.identity.application.ReauthenticationScope;
import com.storyplatform.identity.application.port
        .ReauthenticationGrantRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;

@Repository
public class JdbcReauthenticationGrantRepository
        implements ReauthenticationGrantRepository {

    private final JdbcClient jdbc;

    public JdbcReauthenticationGrantRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void save(Grant grant) {
        jdbc.sql("""
                        INSERT INTO reauthentication_grants (
                            id, token_hash, actor_id, scope, target_type,
                            target_id, expires_at, consumed_at, created_at
                        ) VALUES (
                            :id, :tokenHash, :actorId, :scope, :targetType,
                            :targetId, :expiresAt, :consumedAt, :createdAt
                        )
                        """)
                .param("id", grant.id())
                .param("tokenHash", grant.tokenHash())
                .param("actorId", grant.actorId())
                .param("scope", grant.scope().name())
                .param("targetType", grant.targetType())
                .param("targetId", grant.targetId())
                .param("expiresAt", grant.expiresAt())
                .param("consumedAt", grant.consumedAt())
                .param("createdAt", grant.createdAt())
                .update();
    }

    @Override
    public boolean consume(
            String tokenHash,
            String actorId,
            ReauthenticationScope scope,
            String targetType,
            String targetId,
            Instant consumedAt
    ) {
        return jdbc.sql("""
                        UPDATE reauthentication_grants
                        SET consumed_at = :consumedAt
                        WHERE token_hash = :tokenHash
                          AND actor_id = :actorId
                          AND scope = :scope
                          AND target_type = :targetType
                          AND target_id = :targetId
                          AND consumed_at IS NULL
                          AND expires_at > :consumedAt
                        """)
                .param("consumedAt", consumedAt)
                .param("tokenHash", tokenHash)
                .param("actorId", actorId)
                .param("scope", scope.name())
                .param("targetType", targetType)
                .param("targetId", targetId)
                .update() == 1;
    }
}
