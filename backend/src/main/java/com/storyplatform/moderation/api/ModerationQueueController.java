package com.storyplatform.moderation.api;

import com.storyplatform.moderation.application.ModerationQueueException;
import com.storyplatform.moderation.application.ModerationQueueOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public final class ModerationQueueController {

    private final ModerationQueueOperations queue;
    private final JwtPrivilegeEvaluator privileges;

    public ModerationQueueController(
            ModerationQueueOperations queue,
            JwtPrivilegeEvaluator privileges
    ) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.privileges = Objects.requireNonNull(
                privileges,
                "privileges"
        );
    }

    @GetMapping("/moderation/cases")
    public ResponseEntity<ModerationQueueOperations.ReviewPage> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "PUBLISHING") String type,
            @RequestParam(defaultValue = "OPEN") String state,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor
    ) {
        authorize(jwt, PrivilegedCapability.MODERATION_QUEUE_READ);
        if (!"PUBLISHING".equals(type) || !"OPEN".equals(state)) {
            throw invalidFilter();
        }
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(queue.list(limit, cursor));
        } catch (ModerationQueueException exception) {
            throw problem(exception);
        }
    }

    @PatchMapping("/moderation/cases/{reviewId}")
    public ResponseEntity<ModerationQueueOperations.ReviewCase> claim(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String reviewId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
    ) {
        authorize(jwt, PrivilegedCapability.MODERATION_DECIDE);
        try {
            var claimed = queue.claim(
                    jwt.getSubject(),
                    reviewId,
                    version(ifMatch)
            );
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(
                            HttpHeaders.ETAG,
                            "\"" + claimed.version() + "\""
                    )
                    .body(claimed);
        } catch (ModerationQueueException exception) {
            throw problem(exception);
        }
    }

    @GetMapping("/moderation/cases/{reviewId}")
    public ResponseEntity<ModerationQueueOperations.ReviewDetail> detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String reviewId
    ) {
        authorize(jwt, PrivilegedCapability.MODERATION_QUEUE_READ);
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(queue.detail(reviewId));
        } catch (ModerationQueueException exception) {
            throw problem(exception);
        }
    }

    private void authorize(
            Jwt jwt,
            PrivilegedCapability capability
    ) {
        if (!privileges.allows(jwt, capability)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "MODERATION_FORBIDDEN",
                    "Moderation request rejected",
                    "Moderator privileges are required."
            );
        }
    }

    private static long version(String value) {
        if (value == null || !value.matches("\"[1-9][0-9]*\"")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Moderation request rejected",
                    "If-Match must contain one quoted positive version."
            );
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Moderation request rejected",
                    "If-Match version is outside the supported range."
            );
        }
    }

    private static ApiException invalidFilter() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "MODERATION_FILTER_INVALID",
                "Moderation request rejected",
                "Only the open publishing queue is supported."
        );
    }

    private static ApiException problem(
            ModerationQueueException exception
    ) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return new ApiException(
                status,
                exception.code(),
                "Moderation request rejected",
                exception.getMessage()
        );
    }
}
