package com.storyplatform.community.api;

import com.storyplatform.community.application.CommentException;
import com.storyplatform.community.application.CommentOperations;
import com.storyplatform.community.domain.Comment;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@RestController
public final class CommentController {

    private final CommentOperations comments;

    public CommentController(CommentOperations comments) {
        this.comments = Objects.requireNonNull(comments);
    }

    @GetMapping("/comments")
    public ResponseEntity<CommentOperations.CommentPage> list(
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .body(comments.list(new CommentOperations.ListQuery(
                            type(targetType),
                            uuid(targetId),
                            cursor,
                            limit
                    )));
        } catch (CommentException exception) {
            throw problem(exception);
        }
    }

    @PostMapping("/comments")
    public ResponseEntity<CommentOperations.CommentView> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        try {
            CommentOperations.CommentView view = comments.create(
                    jwt.getSubject(),
                    new CommentOperations.CreateCommand(
                            type(request.targetType()),
                            uuid(request.targetId()),
                            nullableUuid(request.parentId()),
                            request.body()
                    )
            );
            return ResponseEntity.created(
                            URI.create("/api/v1/comments/" + view.id())
                    )
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.ETAG, etag(view.version()))
                    .body(view);
        } catch (CommentException exception) {
            throw problem(exception);
        }
    }

    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<CommentOperations.CommentView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String commentId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody UpdateCommentRequest request
    ) {
        try {
            CommentOperations.CommentView view = comments.update(
                    jwt.getSubject(),
                    uuid(commentId),
                    version(ifMatch),
                    request.body()
            );
            return response(view);
        } catch (CommentException exception) {
            throw problem(exception);
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<CommentOperations.CommentView> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String commentId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
    ) {
        try {
            return response(comments.delete(
                    jwt.getSubject(),
                    uuid(commentId),
                    version(ifMatch)
            ));
        } catch (CommentException exception) {
            throw problem(exception);
        }
    }

    private static ResponseEntity<CommentOperations.CommentView> response(
            CommentOperations.CommentView view
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.ETAG, etag(view.version()))
                .body(view);
    }

    private static Comment.TargetType type(String value) {
        try {
            return Comment.TargetType.valueOf(
                    value.toUpperCase(Locale.ROOT)
            );
        } catch (RuntimeException exception) {
            throw invalid("Comment target type is invalid.");
        }
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid("Comment identifier is invalid.");
        }
    }

    private static String nullableUuid(String value) {
        return value == null ? null : uuid(value);
    }

    private static long version(String value) {
        if (value == null || !value.matches("\"[1-9][0-9]*\"")) {
            throw invalid("If-Match must contain a quoted positive version.");
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw invalid("If-Match version is outside the supported range.");
        }
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static ApiException problem(CommentException exception) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return new ApiException(
                status,
                exception.code(),
                "Comment request rejected",
                exception.getMessage(),
                exception.retryAfterSeconds() > 0
                        ? Duration.ofSeconds(exception.retryAfterSeconds())
                        : null
        );
    }

    private static ApiException invalid(String detail) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "COMMENT_INVALID",
                "Comment request rejected",
                detail
        );
    }

    public record CreateCommentRequest(
            @NotBlank String targetType,
            @NotBlank String targetId,
            String parentId,
            @NotBlank @Size(max = 5_000) String body
    ) {
    }

    public record UpdateCommentRequest(
            @NotBlank @Size(max = 5_000) String body
    ) {
    }
}
