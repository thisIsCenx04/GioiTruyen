package com.storyplatform.moderation.api;

import com.storyplatform.moderation.application.ModerationAppealException;
import com.storyplatform.moderation.application.ModerationAppealOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
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

@RestController
public final class ModerationAppealController {

    private final ModerationAppealOperations appeals;
    private final JwtPrivilegeEvaluator privileges;

    public ModerationAppealController(
            ModerationAppealOperations appeals,
            JwtPrivilegeEvaluator privileges
    ) {
        this.appeals = Objects.requireNonNull(appeals, "appeals");
        this.privileges = Objects.requireNonNull(
                privileges,
                "privileges"
        );
    }

    @PostMapping("/moderation/cases/{reviewId}/appeals")
    public ResponseEntity<ModerationAppealOperations.AppealView> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String reviewId,
            @Valid @RequestBody CreateAppealRequest request
    ) {
        try {
            var created = appeals.create(
                    jwt.getSubject(),
                    reviewId,
                    request.statement()
            );
            return ResponseEntity.created(URI.create(
                            "/moderation/cases/" + reviewId
                                    + "/appeals/" + created.id()
                    ))
                    .cacheControl(CacheControl.noStore())
                    .body(created);
        } catch (ModerationAppealException exception) {
            throw problem(exception);
        }
    }

    @PostMapping(
            "/moderation/cases/{reviewId}/appeals/{appealId}/decisions"
    )
    public ResponseEntity<ModerationAppealOperations.AppealView> decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String reviewId,
            @PathVariable String appealId,
            @Valid @RequestBody AppealDecisionRequest request
    ) {
        if (!privileges.allows(
                jwt,
                PrivilegedCapability.MODERATION_DECIDE
        )) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "MODERATION_FORBIDDEN",
                    "Appeal decision rejected",
                    "Moderator privileges are required."
            );
        }
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(appeals.decide(
                            jwt.getSubject(),
                            reviewId,
                            appealId,
                            request.decision(),
                            request.reasonCode(),
                            request.note()
                    ));
        } catch (ModerationAppealException exception) {
            throw problem(exception);
        }
    }

    private static ApiException problem(
            ModerationAppealException exception
    ) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return new ApiException(
                status,
                exception.code(),
                "Appeal request rejected",
                exception.getMessage()
        );
    }

    public record CreateAppealRequest(
            @NotBlank @Size(max = 4000) String statement
    ) {
    }

    public record AppealDecisionRequest(
            @NotNull ModerationAppealOperations.AppealDecision decision,
            @NotBlank @Size(max = 64) String reasonCode,
            @NotBlank @Size(max = 2000) String note
    ) {
    }
}
