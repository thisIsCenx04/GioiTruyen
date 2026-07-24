package com.storyplatform.publishing.api;

import com.storyplatform.publishing.application
        .PublishingSubmissionException;
import com.storyplatform.publishing.application
        .PublishingSubmissionOperations;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

@RestController
public final class PublishingSubmissionController {

    private final PublishingSubmissionOperations submissions;

    public PublishingSubmissionController(
            PublishingSubmissionOperations submissions
    ) {
        this.submissions = Objects.requireNonNull(
                submissions,
                "submissions"
        );
    }

    @PostMapping("/teams/{teamId}/stories/{storyId}/submit")
    public ResponseEntity<PublishingSubmissionOperations.SubmissionView>
            submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @PathVariable String storyId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        try {
            var review = submissions.submit(
                    jwt.getSubject(),
                    uuid(teamId),
                    uuid(storyId),
                    idempotencyKey
            );
            return ResponseEntity.accepted()
                    .location(URI.create(
                            "/api/v1/moderation/reviews/"
                                    + review.reviewId()
                    ))
                    .cacheControl(CacheControl.noStore())
                    .header(
                            HttpHeaders.ETAG,
                            "\"" + review.version() + "\""
                    )
                    .body(review);
        } catch (PublishingSubmissionException exception) {
            throw problem(exception);
        }
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IDENTIFIER_INVALID",
                    "Publishing submission rejected",
                    "The Team or story identifier is invalid."
            );
        }
    }

    private static ApiException problem(
            PublishingSubmissionException exception
    ) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return new ApiException(
                status,
                exception.code(),
                "Publishing submission rejected",
                exception.getMessage()
        );
    }
}
