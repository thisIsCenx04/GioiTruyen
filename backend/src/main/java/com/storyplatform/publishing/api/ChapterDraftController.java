package com.storyplatform.publishing.api;

import com.storyplatform.publishing.application.ChapterDraftException;
import com.storyplatform.publishing.application.ChapterDraftOperations;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

@RestController
public final class ChapterDraftController {

    private final ChapterDraftOperations chapters;

    public ChapterDraftController(ChapterDraftOperations chapters) {
        this.chapters = Objects.requireNonNull(chapters, "chapters");
    }

    @PostMapping("/teams/{teamId}/stories/{storyId}/chapters")
    public ResponseEntity<ChapterDraftOperations.ChapterView> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @PathVariable String storyId,
            @Valid @RequestBody CreateChapterDraftRequest request
    ) {
        try {
            ChapterDraftOperations.ChapterView chapter = chapters.create(
                    jwt.getSubject(),
                    uuid(teamId),
                    uuid(storyId),
                    new ChapterDraftOperations.CreateCommand(
                            request.number(),
                            request.title(),
                            request.contentHtml()
                    )
            );
            return ResponseEntity.created(URI.create(
                            "/api/v1/teams/" + chapter.teamId()
                                    + "/stories/" + chapter.storyId()
                                    + "/chapters/" + chapter.id()
                    ))
                    .cacheControl(CacheControl.noStore())
                    .header(
                            HttpHeaders.ETAG,
                            "\"" + chapter.version() + "\""
                    )
                    .body(chapter);
        } catch (ChapterDraftException exception) {
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
                    "Chapter draft request rejected",
                    "The Team or story identifier is invalid."
            );
        }
    }

    private static ApiException problem(ChapterDraftException exception) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return new ApiException(
                status,
                exception.code(),
                "Chapter draft request rejected",
                exception.getMessage()
        );
    }
}
