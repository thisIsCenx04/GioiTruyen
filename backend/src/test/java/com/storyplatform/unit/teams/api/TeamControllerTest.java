package com.storyplatform.unit.teams.api;

import com.storyplatform.shared.api.ApiException;
import com.storyplatform.teams.api.CreateTeamRequest;
import com.storyplatform.teams.api.TeamController;
import com.storyplatform.teams.api.UpdateTeamRequest;
import com.storyplatform.teams.application.TeamConflictException;
import com.storyplatform.teams.application.TeamNotFoundException;
import com.storyplatform.teams.application.TeamOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamControllerTest {

    private static final String TEAM_ID =
            "73457d55-9602-4bcd-bbf0-e38b99c6c56e";
    private TeamOperations operations;
    private TeamController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        operations = mock(TeamOperations.class);
        controller = new TeamController(operations);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("owner-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }

    @Test
    void routesPublicReadsAndOwnerWrites() {
        when(operations.list(20)).thenReturn(List.of());
        controller.list(20);
        controller.get(TEAM_ID);
        controller.create(jwt, new CreateTeamRequest(
                "lam-da",
                "Lâm Dạ",
                ""
        ));
        controller.update(jwt, TEAM_ID, new UpdateTeamRequest(
                "Tên mới",
                "",
                2L
        ));

        verify(operations).list(20);
        verify(operations).get(TEAM_ID);
        verify(operations).create("owner-1", "lam-da", "Lâm Dạ", "");
        verify(operations).update(
                "owner-1",
                TEAM_ID,
                2,
                "Tên mới",
                ""
        );
    }

    @Test
    void rejectsInvalidListAndIdentifierInputs() {
        assertCode(() -> controller.list(0), "LIMIT_INVALID");
        assertCode(() -> controller.get("invalid"), "TEAM_ID_INVALID");
    }

    @Test
    void mapsDomainFailuresWithoutLeakingOwnership() {
        when(operations.get(TEAM_ID))
                .thenThrow(new TeamNotFoundException());
        assertCode(() -> controller.get(TEAM_ID), "TEAM_NOT_FOUND");

        when(operations.create(
                "owner-1",
                "lam-da",
                "Lâm Dạ",
                ""
        )).thenThrow(new TeamConflictException(
                "TEAM_SLUG_TAKEN",
                "taken"
        ));
        assertCode(() -> controller.create(jwt, new CreateTeamRequest(
                "lam-da",
                "Lâm Dạ",
                ""
        )), "TEAM_SLUG_TAKEN");

        when(operations.update(
                "owner-1",
                TEAM_ID,
                1,
                "Tên mới",
                ""
        )).thenThrow(new TeamNotFoundException());
        assertCode(() -> controller.update(
                jwt,
                TEAM_ID,
                new UpdateTeamRequest("Tên mới", "", 1L)
        ), "TEAM_NOT_FOUND");
    }

    private static void assertCode(Runnable operation, String code) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(
                                exception.code()
                        ).isEqualTo(code));
    }
}
