package com.storyplatform.community.api;

import com.storyplatform.community.application.CommentException;
import com.storyplatform.community.application.CommentOperations;
import com.storyplatform.community.domain.Comment;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
public final class CommentController {

    private final CommentOperations operations;

    public CommentController(CommentOperations operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    @GetMapping("/comments")
    public CommentOperations.CommentPage list(
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        Comment.TargetType type = parseTargetType(targetType);
        validateUuid(targetId);
        return operations.list(new CommentOperations.ListQuery(
                type, targetId, cursor, limit
        ));
    }

    @PostMapping(
            path = "/comments",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public CommentOperations.CommentView create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        Comment.TargetType type = parseTargetType(request.targetType());
        validateUuid(request.targetId());
        return operations.create(
                jwt.getSubject(),
                new CommentOperations.CreateCommand(
                        type,
                        request.targetId(),
                        request.parentId(),
                        request.body()
                )
        );
    }

    @PutMapping(
            path = "/comments/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public CommentOperations.CommentView update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateCommentRequest request
    ) {
        long version = parseEtag(ifMatch);
        return operations.update(jwt.getSubject(), id, version, request.body());
    }

    @DeleteMapping("/comments/{id}")
    public CommentOperations.CommentView delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestHeader("If-Match") String ifMatch
    ) {
        long version = parseEtag(ifMatch);
        return operations.delete(jwt.getSubject(), id, version);
    }

    private static Comment.TargetType parseTargetType(String value) {
        try {
            return Comment.TargetType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "COMMENT_INVALID_TARGET_TYPE",
                    "Invalid target type",
                    "Unknown target type: " + value
            );
        }
    }

    private static void validateUuid(String id) {
        if (id == null || !id.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        )) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "COMMENT_INVALID_ID",
                    "Invalid identifier",
                    "Not a valid UUID: " + id
            );
        }
    }

    private static long parseEtag(String etag) {
        // Expect quoted numeric value e.g. "\"3\""
        if (etag == null || !etag.startsWith("\"") || !etag.endsWith("\"")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "COMMENT_INVALID_ETAG",
                    "Invalid ETag",
                    "ETag must be a quoted integer, e.g. \"\\\"3\\\"\""
            );
        }
        try {
            return Long.parseLong(etag.substring(1, etag.length() - 1));
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "COMMENT_INVALID_ETAG",
                    "Invalid ETag",
                    "ETag value is not a valid integer"
            );
        }
    }

    public record CreateCommentRequest(
            @NotBlank String targetType,
            @NotBlank String targetId,
            String parentId,
            @NotBlank String body
    ) {
    }

    public record UpdateCommentRequest(
            @NotBlank String body
    ) {
    }
}
