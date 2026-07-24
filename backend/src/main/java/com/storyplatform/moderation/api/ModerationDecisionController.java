package com.storyplatform.moderation.api;

import com.storyplatform.moderation.application
        .ModerationDecisionException;
import com.storyplatform.moderation.application
        .ModerationDecisionOperations;
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

@RestController
public final class ModerationDecisionController {

    private final ModerationDecisionOperations decisions;
    private final JwtPrivilegeEvaluator privileges;

    public ModerationDecisionController(
            ModerationDecisionOperations decisions,
            JwtPrivilegeEvaluator privileges
    ) {
        this.decisions = Objects.requireNonNull(
                decisions,
                "decisions"
        );
        this.privileges = Objects.requireNonNull(
                privileges,
                "privileges"
        );
    }

    @PostMapping("/moderation/cases/{reviewId}/decisions")
    public ResponseEntity<ModerationDecisionOperations.DecisionView>
            decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String reviewId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody ModerationDecisionRequest request
    ) {
        if (!privileges.allows(
                jwt,
                PrivilegedCapability.MODERATION_DECIDE
        )) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "MODERATION_FORBIDDEN",
                    "Moderation decision rejected",
                    "Moderator privileges are required."
            );
        }
        try {
            var result = decisions.decide(
                    jwt.getSubject(),
                    reviewId,
                    version(ifMatch),
                    new ModerationDecisionOperations.DecisionCommand(
                            request.decision(),
                            request.reasonCode(),
                            request.note(),
                            request.evidenceRefs(),
                            request.policyVersion()
                    )
            );
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(
                            HttpHeaders.ETAG,
                            "\"" + result.version() + "\""
                    )
                    .body(result);
        } catch (ModerationDecisionException exception) {
            HttpStatus status = exception.kind()
                    == ModerationDecisionException.Kind.INVALID
                    ? HttpStatus.BAD_REQUEST
                    : HttpStatus.CONFLICT;
            throw new ApiException(
                    status,
                    exception.code(),
                    "Moderation decision rejected",
                    exception.getMessage()
            );
        }
    }

    private static long version(String value) {
        if (value == null || !value.matches("\"[1-9][0-9]*\"")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Moderation decision rejected",
                    "If-Match must contain one quoted positive version."
            );
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Moderation decision rejected",
                    "If-Match version is outside the supported range."
            );
        }
    }
}
