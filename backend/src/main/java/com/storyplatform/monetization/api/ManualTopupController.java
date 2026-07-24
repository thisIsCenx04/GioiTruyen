package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.ManualTopupException;
import com.storyplatform.monetization.application.ManualTopupOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
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
public final class ManualTopupController {

    private final ManualTopupOperations approvals;
    private final JwtPrivilegeEvaluator privileges;

    public ManualTopupController(
            ManualTopupOperations approvals,
            JwtPrivilegeEvaluator privileges
    ) {
        this.approvals = Objects.requireNonNull(approvals);
        this.privileges = Objects.requireNonNull(privileges);
    }

    @PostMapping("/admin/topups/{topupId}/approve")
    public ResponseEntity<ManualTopupOperations.Approval> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String topupId,
            @RequestHeader("Scoped-Reauthentication")
            String reauthentication,
            @Valid @RequestBody ApproveManualTopupRequest request
    ) {
        if (!privileges.allows(
                jwt,
                PrivilegedCapability.FINANCE_REVIEW
        )) {
            throw apiException(new ManualTopupException(
                    "Finance review privileges are required.",
                    ManualTopupException.Kind.FORBIDDEN
            ));
        }
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(approvals.approve(
                            jwt.getSubject(),
                            topupId,
                            reauthentication,
                            request.reason(),
                            request.evidenceReference()
                    ));
        } catch (ManualTopupException exception) {
            throw apiException(exception);
        }
    }

    private static ApiException apiException(
            ManualTopupException exception
    ) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return new ApiException(
                status,
                "MANUAL_TOPUP_" + exception.kind(),
                "Manual top-up approval rejected",
                exception.getMessage()
        );
    }
}
