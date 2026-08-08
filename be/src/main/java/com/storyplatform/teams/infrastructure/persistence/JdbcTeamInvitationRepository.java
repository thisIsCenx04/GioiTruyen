package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.application.port.TeamInvitationRepository;
import com.storyplatform.teams.domain.TeamInvitation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Repository
public class JdbcTeamInvitationRepository
        implements TeamInvitationRepository {

    private static final TypeReference<Set<String>> STRING_SET =
            new TypeReference<>() {
            };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcTeamInvitationRepository(
            JdbcClient jdbc,
            ObjectMapper json
    ) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(TeamInvitation invitation) {
        jdbc.sql("""
                        INSERT INTO team_invitations (
                            id, team_id, target_user_id, invited_by,
                            permissions, token_hash, idempotency_key, state,
                            expires_at, created_at, accepted_at, version
                        ) VALUES (
                            :id, :teamId, :targetUserId, :invitedBy,
                            :permissions, :tokenHash, :idempotencyKey, :state,
                            :expiresAt, :createdAt, :acceptedAt, :version
                        )
                        """)
                .param("id", invitation.id())
                .param("teamId", invitation.teamId())
                .param("targetUserId", invitation.targetUserId())
                .param("invitedBy", invitation.invitedBy())
                .param("permissions", permissions(invitation.permissions()))
                .param("tokenHash", invitation.tokenHash())
                .param("idempotencyKey", invitation.idempotencyKey())
                .param("state", invitation.state().name())
                .param("expiresAt", invitation.expiresAt())
                .param("createdAt", invitation.createdAt())
                .param("acceptedAt", invitation.acceptedAt())
                .param("version", invitation.version())
                .update();
    }

    @Override
    public Optional<TeamInvitation> findByIdempotencyKey(
            String teamId,
            String key
    ) {
        return jdbc.sql(selectInvitation() + """
                         WHERE team_id = :teamId
                           AND idempotency_key = :idempotencyKey
                        """)
                .param("teamId", teamId)
                .param("idempotencyKey", key)
                .query(this::mapInvitation)
                .optional();
    }

    @Override
    public Optional<TeamInvitation> findByTokenHash(String tokenHash) {
        return jdbc.sql(selectInvitation() + """
                         WHERE token_hash = :tokenHash
                        """)
                .param("tokenHash", tokenHash)
                .query(this::mapInvitation)
                .optional();
    }

    @Override
    public boolean accept(
            String invitationId,
            long version,
            Instant acceptedAt
    ) {
        return jdbc.sql("""
                        UPDATE team_invitations
                        SET state = 'ACCEPTED',
                            accepted_at = :acceptedAt,
                            version = version + 1
                        WHERE id = :invitationId
                          AND state = 'PENDING'
                          AND version = :version
                          AND expires_at > :acceptedAt
                        """)
                .param("acceptedAt", acceptedAt)
                .param("invitationId", invitationId)
                .param("version", version)
                .update() == 1;
    }

    private TeamInvitation mapInvitation(
            ResultSet result,
            int rowNumber
    ) throws SQLException {
        return new TeamInvitation(
                result.getString("id"),
                result.getString("team_id"),
                result.getString("target_user_id"),
                result.getString("invited_by"),
                readPermissions(result.getString("permissions")),
                result.getString("token_hash"),
                result.getString("idempotency_key"),
                TeamInvitation.State.valueOf(result.getString("state")),
                result.getTimestamp("expires_at").toInstant(),
                result.getTimestamp("created_at").toInstant(),
                nullableInstant(result, "accepted_at"),
                result.getLong("version")
        );
    }

    private String permissions(Set<String> values) {
        try {
            return json.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "Unable to serialize invitation permissions",
                    exception
            );
        }
    }

    private Set<String> readPermissions(String value) throws SQLException {
        try {
            return json.readValue(value, STRING_SET);
        } catch (JacksonException exception) {
            throw new SQLException(
                    "Invalid invitation permissions",
                    exception
            );
        }
    }

    private static Instant nullableInstant(
            ResultSet result,
            String column
    ) throws SQLException {
        var value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String selectInvitation() {
        return """
                SELECT id, team_id, target_user_id, invited_by, permissions,
                       token_hash, idempotency_key, state, expires_at,
                       created_at, accepted_at, version
                FROM team_invitations
                """;
    }
}
