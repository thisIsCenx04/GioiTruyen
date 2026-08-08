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

    private final TeamAnalyticsOperations operations;

    public TeamAnalyticsController(TeamAnalyticsOperations operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    @GetMapping("/teams/{teamId}/analytics")
    public ResponseEntity<TeamAnalyticsOperations.TeamAnalyticsReport> report(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @RequestParam(defaultValue = "30D") String period
    ) {
        try {
            var report = operations.report(jwt.getSubject(), teamId, period);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(report);
        } catch (TeamAnalyticsException exception) {
            throw switch (exception.kind()) {
                case FORBIDDEN -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "TEAM_ANALYTICS_FORBIDDEN",
                        "Access to team analytics denied",
                        exception.getMessage()
                );
                case INVALID -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "TEAM_ANALYTICS_INVALID",
                        "Invalid analytics query",
                        exception.getMessage()
                );
            };
        }
    }
}
