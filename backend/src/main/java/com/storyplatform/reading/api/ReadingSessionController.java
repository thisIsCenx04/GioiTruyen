package com.storyplatform.reading.api;

import com.storyplatform.reading.application.ReadingSessionException;
import com.storyplatform.reading.application.ReadingSessionOperations;
import com.storyplatform.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

@RestController
public final class ReadingSessionController {

    private final ReadingSessionOperations sessions;

    public ReadingSessionController(ReadingSessionOperations sessions) {
        this.sessions = Objects.requireNonNull(sessions);
    }

    @PostMapping("/reading-sessions")
    public ResponseEntity<ReadingSessionOperations.SessionGrant> start(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StartReadingSessionRequest request,
            HttpServletRequest servletRequest
    ) {
        try {
            var grant = sessions.start(
                    new ReadingSessionOperations.StartCommand(
                            request.storyId(),
                            request.chapterId(),
                            request.anonymousId(),
                            jwt == null ? null : jwt.getSubject(),
                            servletRequest.getRemoteAddr()
                    )
            );
            return ResponseEntity.created(URI.create(
                            "/api/v1/reading-sessions/"
                                    + grant.sessionId()
                    ))
                    .cacheControl(CacheControl.noStore())
                    .body(grant);
        } catch (ReadingSessionException exception) {
            throw problem(exception);
        }
    }

    private static ApiException problem(
            ReadingSessionException exception
    ) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        Duration retryAfter =
                exception.kind()
                        == ReadingSessionException.Kind.RATE_LIMITED
                        ? Duration.ofSeconds(
                                exception.retryAfterSeconds()
                        )
                        : null;
        return new ApiException(
                status,
                exception.code(),
                "Reading session request rejected",
                exception.getMessage(),
                retryAfter
        );
    }
}
