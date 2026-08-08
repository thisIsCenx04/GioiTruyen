package com.storyplatform.discovery.api;

import com.storyplatform.discovery.application.RankingOperations;
import com.storyplatform.shared.api.ApiException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@RestController
public final class RankingController {

    private final RankingOperations rankings;

    public RankingController(RankingOperations rankings) {
        this.rankings = Objects.requireNonNull(rankings, "rankings");
    }

    @GetMapping("/rankings/stories")
    public ResponseEntity<RankingOperations.RankingPage> stories(
            @RequestParam(defaultValue = "WEEK") String period,
            @RequestParam(defaultValue = "VALID_VIEWS") String metric,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf,
            @RequestParam(defaultValue = "10") int limit
    ) {
        try {
            return response(rankings.stories(period, metric, asOf, limit));
        } catch (IllegalArgumentException exception) {
            throw invalid(exception);
        }
    }

    @GetMapping("/rankings/teams")
    public ResponseEntity<RankingOperations.RankingPage> teams(
            @RequestParam(defaultValue = "WEEK") String period,
            @RequestParam(defaultValue = "VALID_VIEWS") String metric,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf,
            @RequestParam(defaultValue = "10") int limit
    ) {
        try {
            return response(rankings.teams(period, metric, asOf, limit));
        } catch (IllegalArgumentException exception) {
            throw invalid(exception);
        }
    }

    private static ResponseEntity<RankingOperations.RankingPage> response(
            RankingOperations.RankingPage page
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))
                        .cachePublic())
                .body(page);
    }

    private static ApiException invalid(IllegalArgumentException exception) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "RANKING_QUERY_INVALID",
                "Ranking query rejected",
                exception.getMessage()
        );
    }
}
