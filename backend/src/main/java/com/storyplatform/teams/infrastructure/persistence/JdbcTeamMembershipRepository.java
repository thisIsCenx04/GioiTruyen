package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.application.port.TeamMembershipRepository;
import com.storyplatform.teams.domain.TeamMembership;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository
public class JdbcTeamMembershipRepository
        implements TeamMembershipRepository {

    private static final TypeReference<Set<String>> STRING_SET =
            new TypeReference<>() {
            };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcTeamMembershipRepository(
            JdbcClient jdbc,
            ObjectMapper json
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public void insertOwner(TeamMembership membership) {
        insert(membership);
    }

    @Override
    public void insert(TeamMembership membership) {
        jdbc.sql("""
                        INSERT INTO team_memberships (
                            team_id, user_id, role, permissions, state,
                            joined_at, updated_at, version
                        ) VALUES (
                            :teamId, :userId, :role, :permissions, :state,
                            :joinedAt, :joinedAt, :version
                        )
                        """)
                .param("teamId", membership.teamId())
                .param("userId", membership.userId())
                .param("role", membership.role().name())
                .param("permissions", writePermissions(
                        membership.permissions()
                ))
                .param("state", membership.state().name())
                .param("joinedAt", membership.joinedAt())
                .param("version", membership.version())
                .update();
    }

    @Override
    public Optional<TeamMembership> find(
            String teamId,
            String userId
    ) {
        return jdbc.sql(selectMembership() + """
                         WHERE team_id = :teamId AND user_id = :userId
                        """)
                .param("teamId", teamId)
                .param("userId", userId)
                .query(this::mapMembership)
                .optional();
    }

    @Override
    public List<TeamMembership> list(String teamId) {
        return jdbc.sql(selectMembership() + """
                         WHERE team_id = :teamId
                         ORDER BY role, joined_at, user_id
                        """)
                .param("teamId", teamId)
                .query(this::mapMembership)
                .list();
    }

    @Override
    public boolean activate(
            String teamId,
            String userId,
            long version,
            Instant joinedAt
    ) {
        return jdbc.sql("""
                        UPDATE team_memberships
                        SET state = 'ACTIVE',
                            joined_at = :joinedAt,
                            updated_at = :joinedAt,
                            version = version + 1
                        WHERE team_id = :teamId
                          AND user_id = :userId
                          AND state = 'INVITED'
                          AND version = :version
                        """)
                .param("joinedAt", joinedAt)
                .param("teamId", teamId)
                .param("userId", userId)
                .param("version", version)
                .update() == 1;
    }

    @Override
    public PermissionUpdateResult updatePermissions(
            String teamId,
            String userId,
            long version,
            Set<String> permissions
    ) {
        int updated = jdbc.sql("""
                        UPDATE team_memberships
                        SET permissions = :permissions,
                            updated_at = CURRENT_TIMESTAMP(6),
                            version = version + 1
                        WHERE team_id = :teamId
                          AND user_id = :userId
                          AND role = 'MEMBER'
                          AND state = 'ACTIVE'
                          AND version = :version
                        """)
                .param("permissions", writePermissions(permissions))
                .param("teamId", teamId)
                .param("userId", userId)
                .param("version", version)
                .update();
        if (updated == 1) {
            return PermissionUpdateResult.UPDATED;
        }
        boolean exists = activeMemberExists(teamId, userId);
        return exists
                ? PermissionUpdateResult.VERSION_CONFLICT
                : PermissionUpdateResult.NOT_FOUND;
    }

    @Override
    public RemovalResult revokeMember(
            String teamId,
            String userId,
            long version
    ) {
        Optional<TeamMembership> current = find(teamId, userId);
        if (current.isEmpty()) {
            return RemovalResult.NOT_FOUND_OR_CONFLICT;
        }
        if (current.orElseThrow().role() == TeamMembership.Role.OWNER) {
            return RemovalResult.LAST_OWNER;
        }
        int removed = jdbc.sql("""
                        UPDATE team_memberships
                        SET state = 'REVOKED',
                            updated_at = CURRENT_TIMESTAMP(6),
                            version = version + 1
                        WHERE team_id = :teamId
                          AND user_id = :userId
                          AND version = :version
                          AND state <> 'REVOKED'
                        """)
                .param("teamId", teamId)
                .param("userId", userId)
                .param("version", version)
                .update();
        return removed == 1
                ? RemovalResult.REMOVED
                : RemovalResult.NOT_FOUND_OR_CONFLICT;
    }

    private boolean activeMemberExists(String teamId, String userId) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM team_memberships
                        WHERE team_id = :teamId
                          AND user_id = :userId
                          AND role = 'MEMBER'
                          AND state = 'ACTIVE'
                        """)
                .param("teamId", teamId)
                .param("userId", userId)
                .query(Long.class)
                .single() == 1;
    }

    private TeamMembership mapMembership(
            ResultSet result,
            int rowNumber
    ) throws SQLException {
        String teamId = result.getString("team_id");
        String userId = result.getString("user_id");
        return new TeamMembership(
                teamId + ":" + userId,
                teamId,
                userId,
                TeamMembership.Role.valueOf(result.getString("role")),
                readPermissions(result.getString("permissions")),
                TeamMembership.State.valueOf(result.getString("state")),
                result.getTimestamp("joined_at").toInstant(),
                result.getLong("version")
        );
    }

    private Set<String> readPermissions(String value) throws SQLException {
        try {
            return json.readValue(value, STRING_SET);
        } catch (JacksonException exception) {
            throw new SQLException(
                    "Invalid team membership permissions",
                    exception
            );
        }
    }

    private String writePermissions(Set<String> permissions) {
        try {
            return json.writeValueAsString(permissions);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "Unable to serialize team membership permissions",
                    exception
            );
        }
    }

    private static String selectMembership() {
        return """
                SELECT team_id, user_id, role, permissions, state,
                       joined_at, version
                FROM team_memberships
                """;
    }
}
