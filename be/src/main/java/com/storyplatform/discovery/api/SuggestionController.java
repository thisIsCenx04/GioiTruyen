package com.storyplatform.discovery.api;

import com.storyplatform.discovery.application.SuggestionOperations;
import com.storyplatform.discovery.application.SuggestionRateLimitException;
import com.storyplatform.discovery.application
        .SuggestionRateLimitUnavailableException;
import com.storyplatform.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Objects;

@RestController
public final class SuggestionController {

    private static final CacheControl PUBLIC_CACHE =
            CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic();

    private final SuggestionOperations suggestions;

    public SuggestionController(SuggestionOperations suggestions) {
        this.suggestions = Objects.requireNonNull(
                suggestions,
                "suggestions"
        );
    }

    @GetMapping("/search/suggestions")
    public ResponseEntity<SuggestionOperations.SuggestionResponse> suggest(
            @RequestParam(name = "q") String query,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "8") int limit,
            HttpServletRequest request
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(PUBLIC_CACHE)
                    .body(suggestions.suggest(
                            new SuggestionOperations.SuggestionRequest(
                                    query,
                                    cursor,
                                    limit,
                                    request.getRemoteAddr()
                            )
                    ));
        } catch (SuggestionRateLimitException exception) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "SUGGESTION_RATE_LIMITED",
                    "Suggestion request rejected",
                    exception.getMessage(),
                    Duration.ofSeconds(exception.retryAfterSeconds())
            );
        } catch (SuggestionRateLimitUnavailableException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SUGGESTION_RATE_LIMIT_UNAVAILABLE",
                    "Suggestion service unavailable",
                    "Suggestion abuse protection is temporarily unavailable"
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SUGGESTION_INVALID",
                    "Suggestion request rejected",
                    exception.getMessage()
            );
        }
    }
}
