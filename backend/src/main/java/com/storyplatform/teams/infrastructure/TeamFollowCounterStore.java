package com.storyplatform.teams.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.util.Objects;

public class TeamFollowCounterStore {

    private final JdbcClient jdbc;
    private final Clock clock;

    public TeamFollowCounterStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void applyDelta(String teamId, int delta) {
        if (delta == 1) {
            jdbc.sql("""
                            INSERT INTO team_follow_counters (
                                team_id, follower_count, updated_at
                            ) VALUES (:teamId, 1, :updatedAt)
                            ON DUPLICATE KEY UPDATE
                                follower_count = follower_count + 1,
                                updated_at = VALUES(updated_at)
                            """)
                    .param("teamId", teamId)
                    .param("updatedAt", clock.instant())
                    .update();
            return;
        }
        if (delta != -1) {
            throw new IllegalArgumentException(
                    "follow counter delta must be 1 or -1"
            );
        }
        jdbc.sql("""
                        UPDATE team_follow_counters
                        SET follower_count = follower_count - 1,
                            updated_at = :updatedAt
                        WHERE team_id = :teamId AND follower_count > 0
                        """)
                .param("updatedAt", clock.instant())
                .param("teamId", teamId)
                .update();
    }

    public void reconcile(String teamId, long authoritativeCount) {
        if (authoritativeCount < 0) {
            throw new IllegalArgumentException(
                    "authoritative follow count must not be negative"
            );
        }
        jdbc.sql("""
                        INSERT INTO team_follow_counters (
                            team_id, follower_count, updated_at
                        ) VALUES (:teamId, :followerCount, :updatedAt)
                        ON DUPLICATE KEY UPDATE
                            follower_count = VALUES(follower_count),
                            updated_at = VALUES(updated_at)
                        """)
                .param("teamId", teamId)
                .param("followerCount", authoritativeCount)
                .param("updatedAt", clock.instant())
                .update();
    }
}
