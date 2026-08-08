package com.storyplatform.unit.teams.api;

import com.storyplatform.shared.api.ApiException;
import com.storyplatform.teams.api.TeamFollowController;
import com.storyplatform.teams.application.TeamFollowOperations;
import com.storyplatform.teams.application.TeamNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamFollowControllerTest {

    private static final String TEAM_ID =
            "73457d55-9602-4bcd-bbf0-e38b99c6c56e";
    private TeamFollowOperations operations;
    private TeamFollowController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        operations = mock(TeamFollowOperations.class);
        controller = new TeamFollowController(operations);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("user-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }

    @Test
    void routesStatusFollowAndUnfollow() {
        controller.status(jwt, TEAM_ID);
        controller.follow(jwt, TEAM_ID);
        controller.unfollow(jwt, TEAM_ID);

        verify(operations).status("user-1", TEAM_ID);
        verify(operations).follow("user-1", TEAM_ID);
        verify(operations).unfollow("user-1", TEAM_ID);
    }

    @Test
    void mapsInvalidAndMissingTeam() {
        assertCode(
                () -> controller.follow(jwt, "bad"),
                "TEAM_ID_INVALID"
        );
        when(operations.follow("user-1", TEAM_ID))
                .thenThrow(new TeamNotFoundException());
        assertCode(
                () -> controller.follow(jwt, TEAM_ID),
                "TEAM_NOT_FOUND"
        );
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(
                                exception.code()
                        ).isEqualTo(code));
    }
}
