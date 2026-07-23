package com.storyplatform.unit.teams.api;

import com.storyplatform.shared.api.ApiException;
import com.storyplatform.teams.api.AddTeamMemberRequest;
import com.storyplatform.teams.api.TeamMembershipController;
import com.storyplatform.teams.application.TeamAccessDeniedException;
import com.storyplatform.teams.application.TeamConflictException;
import com.storyplatform.teams.application.TeamInvitationInvalidException;
import com.storyplatform.teams.application.TeamMembershipOperations;
import com.storyplatform.teams.application.TeamNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamMembershipControllerTest {

    private static final String TEAM_ID =
            "73457d55-9602-4bcd-bbf0-e38b99c6c56e";
    private static final String USER_ID =
            "b7383e96-8dd9-414e-82f4-c9e2ccab21bc";

    private TeamMembershipOperations operations;
    private TeamMembershipController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        operations = mock(TeamMembershipOperations.class);
        controller = new TeamMembershipController(operations);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("owner-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }

    @Test
    void routesListInviteAcceptAndRemove() {
        when(operations.list("owner-1", TEAM_ID))
                .thenReturn(List.of());
        controller.list(jwt, TEAM_ID);
        controller.invite(
                jwt,
                TEAM_ID,
                "request-123",
                new AddTeamMemberRequest(
                        USER_ID,
                        Set.of("story:create")
                )
        );
        controller.accept(jwt, "raw-token");
        controller.remove(jwt, TEAM_ID, USER_ID, 2);

        verify(operations).list("owner-1", TEAM_ID);
        verify(operations).invite(
                "owner-1",
                TEAM_ID,
                USER_ID,
                Set.of("story:create"),
                "request-123"
        );
        verify(operations).accept("owner-1", "raw-token");
        verify(operations).remove("owner-1", TEAM_ID, USER_ID, 2);
    }

    @Test
    void rejectsInvalidIdentifiers() {
        assertCode(
                () -> controller.list(jwt, "bad"),
                "IDENTIFIER_INVALID"
        );
        assertCode(
                () -> controller.remove(jwt, TEAM_ID, "bad", 0),
                "IDENTIFIER_INVALID"
        );
    }

    @Test
    void mapsAuthorizationNotFoundConflictAndInvitationFailures() {
        when(operations.list("owner-1", TEAM_ID))
                .thenThrow(new TeamAccessDeniedException());
        assertCode(
                () -> controller.list(jwt, TEAM_ID),
                "TEAM_OWNER_REQUIRED"
        );

        reset(operations);
        when(operations.list("owner-1", TEAM_ID))
                .thenThrow(new TeamNotFoundException());
        assertCode(
                () -> controller.list(jwt, TEAM_ID),
                "TEAM_RESOURCE_NOT_FOUND"
        );

        when(operations.invite(
                "owner-1",
                TEAM_ID,
                USER_ID,
                Set.of("story:create"),
                "request-123"
        )).thenThrow(new TeamConflictException(
                "TEAM_MEMBER_EXISTS",
                "exists"
        ));
        assertCode(() -> controller.invite(
                jwt,
                TEAM_ID,
                "request-123",
                new AddTeamMemberRequest(
                        USER_ID,
                        Set.of("story:create")
                )
        ), "TEAM_MEMBER_EXISTS");

        when(operations.accept("owner-1", "bad"))
                .thenThrow(new TeamInvitationInvalidException());
        assertCode(
                () -> controller.accept(jwt, "bad"),
                "TEAM_INVITATION_INVALID"
        );
    }

    private static void assertCode(Runnable operation, String code) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(
                                exception.code()
                        ).isEqualTo(code));
    }
}
