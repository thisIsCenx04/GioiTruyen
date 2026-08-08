package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.application.port.TeamRepository;
import com.storyplatform.teams.domain.Team;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JdbcTeamRepository implements TeamRepository {

    private static final String SELECT_TEAM = """
            SELECT id, slug, name, description, owner_user_id, state,
                   created_at, updated_at, version
            FROM teams
            """;

    private final JdbcClient jdbc;

    public JdbcTeamRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public boolean insertIfSlugAvailable(Team team) {
        try {
            return jdbc.sql("""
                            INSERT INTO teams (
                                id, slug, name, description, owner_user_id,
                                state, created_at, updated_at, version
                            ) VALUES (
                                :id, :slug, :name, :description, :ownerId,
                                :state, :createdAt, :updatedAt, :version
                            )
                            """)
                    .param("id", team.id())
                    .param("slug", team.slug())
                    .param("name", team.name())
                    .param("description", team.description())
                    .param("ownerId", team.ownerUserId())
                    .param("state", team.state().name())
                    .param("createdAt", team.createdAt())
                    .param("updatedAt", team.updatedAt())
                    .param("version", team.version())
                    .update() == 1;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    @Override
    public Optional<Team> findById(String teamId) {
        return jdbc.sql(SELECT_TEAM + " WHERE id = :id")
                .param("id", teamId)
                .query(JdbcTeamRepository::mapTeam)
                .optional();
    }

    @Override
    public List<Team> listActive(int limit) {
        return jdbc.sql(SELECT_TEAM + """
                         WHERE state = 'ACTIVE'
                         ORDER BY updated_at DESC, id DESC
                         LIMIT :limit
                        """)
                .param("limit", limit)
                .query(JdbcTeamRepository::mapTeam)
                .list();
    }

    @Override
    public UpdateResult updateOwned(
            String teamId,
            String ownerUserId,
            long version,
            String name,
            String description,
            Instant now
    ) {
        int updated = jdbc.sql("""
                        UPDATE teams
                        SET name = :name,
                            description = :description,
                            updated_at = :now,
                            version = version + 1
                        WHERE id = :teamId
                          AND owner_user_id = :ownerUserId
                          AND state = 'ACTIVE'
                          AND version = :version
                        """)
                .param("name", name)
                .param("description", description)
                .param("now", now)
                .param("teamId", teamId)
                .param("ownerUserId", ownerUserId)
                .param("version", version)
                .update();
        if (updated == 1) {
            return UpdateResult.UPDATED;
        }
        boolean owned = jdbc.sql("""
                        SELECT COUNT(*)
                        FROM teams
                        WHERE id = :teamId
                          AND owner_user_id = :ownerUserId
                          AND state = 'ACTIVE'
                        """)
                .param("teamId", teamId)
                .param("ownerUserId", ownerUserId)
                .query(Long.class)
                .single() == 1;
        return owned
                ? UpdateResult.VERSION_CONFLICT
                : UpdateResult.NOT_OWNED_OR_NOT_FOUND;
    }

    private static Team mapTeam(
            ResultSet result,
            int rowNumber
    ) throws SQLException {
        return new Team(
                result.getString("id"),
                result.getString("slug"),
                result.getString("name"),
                result.getString("description"),
                result.getString("owner_user_id"),
                Team.State.valueOf(result.getString("state")),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getLong("version")
        );
    }
}
