package com.storyplatform.analytics.api;

import com.storyplatform.analytics.application.TeamAnalyticsException;
import com.storyplatform.analytics.application.TeamAnalyticsOperations;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public final class TeamAnalyticsController {

    private final TeamAnalyticsOperations analytics;

    public TeamAnalyticsController(TeamAnalyticsOperations analytics) {
        this.analytics = Objects.requireNonNull(analytics);
    }

    @GetMapping("/teams/{teamId}/analytics/views")
    public ResponseEntity<TeamAnalyticsOperations.TeamAnalyticsReport> report(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @RequestParam(defaultValue = "30D") String period
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(analytics.report(jwt.getSubject(), teamId, period));
        } catch (TeamAnalyticsException exception) {
            HttpStatus status = exception.kind()
                    == TeamAnalyticsException.Kind.FORBIDDEN
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;
            throw new ApiException(
                    status,
                    status == HttpStatus.FORBIDDEN
                            ? "TEAM_ANALYTICS_FORBIDDEN"
                            : "TEAM_ANALYTICS_INVALID",
                    "Team analytics request rejected",
                    exception.getMessage()
            );
        }
    }
}
