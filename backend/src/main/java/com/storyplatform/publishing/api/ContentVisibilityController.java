package com.storyplatform.publishing.api;

import com.storyplatform.publishing.application.ContentVisibilityException;
import com.storyplatform.publishing.application.ContentVisibilityOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
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

import java.util.Objects;
import java.util.UUID;

@RestController
public final class ContentVisibilityController {

    private final ContentVisibilityOperations visibility;
    private final JwtPrivilegeEvaluator privileges;

    public ContentVisibilityController(
            ContentVisibilityOperations visibility,
            JwtPrivilegeEvaluator privileges
    ) {
        this.visibility = Objects.requireNonNull(visibility);
        this.privileges = Objects.requireNonNull(privileges);
    }

    @PostMapping("/teams/{teamId}/stories/{storyId}/visibility")
    public ResponseEntity<ContentVisibilityOperations.VisibilityView>
            teamChange(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @PathVariable String storyId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody ContentVisibilityRequest request
    ) {
        try {
            return response(visibility.teamChange(
                    jwt.getSubject(),
                    uuid(teamId),
                    uuid(storyId),
                    version(ifMatch),
                    command(request)
            ));
        } catch (ContentVisibilityException exception) {
            throw problem(exception);
        }
    }

    @PostMapping("/moderation/stories/{storyId}/visibility")
    public ResponseEntity<ContentVisibilityOperations.VisibilityView>
            moderationChange(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String storyId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody ContentVisibilityRequest request
    ) {
        if (!privileges.allows(
                jwt,
                PrivilegedCapability.CONTENT_SUSPEND
        )) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "CONTENT_SUSPEND_FORBIDDEN",
                    "Content visibility request rejected",
                    "Moderator or Admin privileges are required."
            );
        }
        try {
            return response(visibility.moderationChange(
                    jwt.getSubject(),
                    uuid(storyId),
                    version(ifMatch),
                    command(request)
            ));
        } catch (ContentVisibilityException exception) {
            throw problem(exception);
        }
    }

    private static ResponseEntity<
            ContentVisibilityOperations.VisibilityView> response(
            ContentVisibilityOperations.VisibilityView value
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.ETAG,
                        "\"" + value.version() + "\""
                )
                .body(value);
    }

    private static ContentVisibilityOperations.VisibilityCommand command(
            ContentVisibilityRequest request
    ) {
        return new ContentVisibilityOperations.VisibilityCommand(
                request.action(),
                request.reasonCode(),
                request.note()
        );
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IDENTIFIER_INVALID",
                    "Content visibility request rejected",
                    "A supplied identifier is invalid."
            );
        }
    }

    private static long version(String value) {
        if (value == null || !value.matches("\"[1-9][0-9]*\"")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Content visibility request rejected",
                    "If-Match must contain one quoted positive version."
            );
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Content visibility request rejected",
                    "If-Match version is outside the supported range."
            );
        }
    }

    private static ApiException problem(
            ContentVisibilityException exception
    ) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PRECONDITION -> HttpStatus.PRECONDITION_FAILED;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return new ApiException(
                status,
                exception.code(),
                "Content visibility request rejected",
                exception.getMessage()
        );
    }
}
