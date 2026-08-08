package com.storyplatform.unit.analytics.api;

import com.storyplatform.analytics.api.TeamAnalyticsController;
import com.storyplatform.analytics.application.TeamAnalyticsException;
import com.storyplatform.analytics.application.TeamAnalyticsOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamAnalyticsControllerTest {

    @Test
    void returnsPrivateReportAndMapsForbiddenAccess() {
        TeamAnalyticsOperations operations =
                mock(TeamAnalyticsOperations.class);
        Jwt jwt = new Jwt(
                "token",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", "actor")
        );
        var report = new TeamAnalyticsOperations.TeamAnalyticsReport(
                "team",
                "30D",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1),
                new TeamAnalyticsOperations.Totals(0, 0, 0, 0, 0),
                List.of(),
                List.of()
        );
        when(operations.report("actor", "team", "30D"))
                .thenReturn(report)
                .thenThrow(new TeamAnalyticsException(
                        "denied",
                        TeamAnalyticsException.Kind.FORBIDDEN
                ));
        var controller = new TeamAnalyticsController(operations);

        var response = controller.report(jwt, "team", "30D");

        assertThat(response.getBody()).isEqualTo(report);
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
        assertThatThrownBy(() -> controller.report(jwt, "team", "30D"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("TEAM_ANALYTICS_FORBIDDEN");
    }

    @Test
    void mapsInvalidQueryToBadRequestProblem() {
        TeamAnalyticsOperations operations =
                mock(TeamAnalyticsOperations.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("actor");
        when(operations.report("actor", "team", "BAD")).thenThrow(
                new TeamAnalyticsException(
                        "bad",
                        TeamAnalyticsException.Kind.INVALID
                )
        );
        var controller = new TeamAnalyticsController(operations);

        assertThatThrownBy(() -> controller.report(jwt, "team", "BAD"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("TEAM_ANALYTICS_INVALID");
    }
}
