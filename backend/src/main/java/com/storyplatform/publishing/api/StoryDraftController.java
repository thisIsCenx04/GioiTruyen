package com.storyplatform.publishing.api;

import com.storyplatform.publishing.application.StoryDraftException;
import com.storyplatform.publishing.application.StoryDraftOperations;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

@RestController
public final class StoryDraftController {

    private final StoryDraftOperations drafts;

    public StoryDraftController(StoryDraftOperations drafts) {
        this.drafts = Objects.requireNonNull(drafts, "drafts");
    }

    @PostMapping("/teams/{teamId}/stories")
    public ResponseEntity<StoryDraftOperations.DraftView> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateStoryDraftRequest request
    ) {
        try {
            StoryDraftOperations.DraftView draft = drafts.create(
                    jwt.getSubject(),
                    uuid(teamId),
                    idempotencyKey,
                    new StoryDraftOperations.CreateCommand(
                            request.title(),
                            request.synopsis(),
                            request.origin(),
                            request.language(),
                            request.categoryIds(),
                            request.coverAssetId()
                    )
            );
            return ResponseEntity.created(URI.create(
                            "/api/v1/teams/"
                                    + draft.teamId()
                                    + "/stories/"
                                    + draft.id()
                    ))
                    .cacheControl(CacheControl.noStore())
                    .header(
                            HttpHeaders.ETAG,
                            "\"" + draft.version() + "\""
                    )
                    .body(draft);
        } catch (StoryDraftException exception) {
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
                    "Story draft request rejected",
                    "The Team identifier is invalid."
            );
        }
    }

    private static ApiException problem(StoryDraftException exception) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return new ApiException(
                status,
                exception.code(),
                "Story draft request rejected",
                exception.getMessage()
        );
    }
}
