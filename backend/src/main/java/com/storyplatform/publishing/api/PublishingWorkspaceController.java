package com.storyplatform.publishing.api;

import com.storyplatform.publishing.application.PublishingWorkspaceOperations;
import com.storyplatform.publishing.application.StoryDraftException;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
public final class PublishingWorkspaceController {

    private final PublishingWorkspaceOperations workspace;

    public PublishingWorkspaceController(
            PublishingWorkspaceOperations workspace
    ) {
        this.workspace = Objects.requireNonNull(workspace);
    }

    @GetMapping("/teams/{teamId}/stories")
    public ResponseEntity<List<
            PublishingWorkspaceOperations.StorySummary>> stories(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId
    ) {
        try {
            return noStore(workspace.stories(jwt.getSubject(), teamId));
        } catch (StoryDraftException exception) {
            throw problem(exception);
        }
    }

    @GetMapping("/teams/{teamId}/stories/{storyId}")
    public ResponseEntity<PublishingWorkspaceOperations.StorySummary> story(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @PathVariable String storyId
    ) {
        try {
            return noStore(workspace.story(
                    jwt.getSubject(),
                    teamId,
                    storyId
            ));
        } catch (StoryDraftException exception) {
            throw problem(exception);
        }
    }

    @GetMapping("/teams/{teamId}/stories/{storyId}/chapters")
    public ResponseEntity<List<
            PublishingWorkspaceOperations.ChapterEditor>> chapters(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @PathVariable String storyId
    ) {
        try {
            return noStore(workspace.chapters(
                    jwt.getSubject(),
                    teamId,
                    storyId
            ));
        } catch (StoryDraftException exception) {
            throw problem(exception);
        }
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private static ApiException problem(StoryDraftException exception) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION -> HttpStatus.PRECONDITION_FAILED;
        };
        return new ApiException(
                status,
                exception.code(),
                "Publishing workspace request rejected",
                exception.getMessage()
        );
    }
}
