package com.storyplatform.teams.infrastructure.persistence;

import com.storyplatform.teams.application.port.TeamFollowRepository;
import com.storyplatform.teams.domain.TeamFollow;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTeamFollowRepository implements TeamFollowRepository {

    private final JdbcClient jdbc;

    public JdbcTeamFollowRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(TeamFollow follow) {
        try {
            return jdbc.sql("""
                            INSERT INTO team_follows (
                                team_id, user_id, created_at
                            ) VALUES (:teamId, :userId, :createdAt)
                            """)
                    .param("teamId", follow.teamId())
                    .param("userId", follow.userId())
                    .param("createdAt", follow.createdAt())
                    .update() == 1;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    @Override
    public boolean deleteIfPresent(String teamId, String userId) {
        return jdbc.sql("""
                        DELETE FROM team_follows
                        WHERE team_id = :teamId AND user_id = :userId
                        """)
                .param("teamId", teamId)
                .param("userId", userId)
                .update() == 1;
    }

    @Override
    public boolean exists(String teamId, String userId) {
        return jdbc.sql("""
                        SELECT COUNT(*)
                        FROM team_follows
                        WHERE team_id = :teamId AND user_id = :userId
                        """)
                .param("teamId", teamId)
                .param("userId", userId)
                .query(Long.class)
                .single() == 1;
    }

    @Override
    public long count(String teamId) {
        return jdbc.sql("""
                        SELECT COUNT(*) FROM team_follows
                        WHERE team_id = :teamId
                        """)
                .param("teamId", teamId)
                .query(Long.class)
                .single();
    }
}
