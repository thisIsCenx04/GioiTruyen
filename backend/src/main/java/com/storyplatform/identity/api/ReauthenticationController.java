package com.storyplatform.identity.api;

import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.LoginRiskUnavailableException;
import com.storyplatform.identity.application.ReauthenticationUseCase;
import com.storyplatform.identity.application.port.LoginRiskLimiter;
import com.storyplatform.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Objects;

@RestController
@RequestMapping("/auth/reauth")
public class ReauthenticationController {

    private final IdentityService identityService;
    private final LoginRiskLimiter riskLimiter;

    public ReauthenticationController(
            IdentityService identityService,
            LoginRiskLimiter riskLimiter
    ) {
        this.identityService = Objects.requireNonNull(
                identityService,
                "identityService"
        );
        this.riskLimiter = Objects.requireNonNull(
                riskLimiter,
                "riskLimiter"
        );
    }

    @PostMapping("/grants")
    public ReauthenticationGrantResponse issue(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReauthenticationGrantRequest request,
            HttpServletRequest servletRequest
    ) {
        String riskSubject = "reauth:" + jwt.getSubject();
        String address = servletRequest.getRemoteAddr();
        ensureAllowed(riskSubject, address);
        ReauthenticationUseCase.IssueResult result =
                identityService.issueReauthenticationGrant(
                        new ReauthenticationUseCase.IssueCommand(
                                jwt.getSubject(),
                                request.password(),
                                request.mfaCode(),
                                request.scope(),
                                request.targetType(),
                                request.targetId()
                        )
                );
        if (result.status()
                == ReauthenticationUseCase.Status.INVALID_TARGET) {
            throw rejected(
                    HttpStatus.BAD_REQUEST,
                    "REAUTH_TARGET_INVALID"
            );
        }
        if (result.status()
                == ReauthenticationUseCase.Status.INVALID_PROOF) {
            recordFailure(riskSubject, address);
            throw rejected(
                    HttpStatus.UNAUTHORIZED,
                    "REAUTHENTICATION_FAILED"
            );
        }
        recordSuccess(riskSubject, address);
        return new ReauthenticationGrantResponse(
                result.token(),
                "Scoped-Reauthentication",
                result.expiresInSeconds(),
                result.expiresAt()
        );
    }

    private void ensureAllowed(String subject, String address) {
        try {
            if (!riskLimiter.allow(subject, address)) {
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "REAUTH_RATE_LIMITED",
                        "Reauthentication rejected",
                        "Please try again later.",
                        Duration.ofSeconds(
                                riskLimiter.retryAfterSeconds()
                        )
                );
            }
        } catch (LoginRiskUnavailableException exception) {
            throw unavailable();
        }
    }

    private void recordFailure(String subject, String address) {
        try {
            riskLimiter.recordFailure(subject, address);
        } catch (LoginRiskUnavailableException exception) {
            throw unavailable();
        }
    }

    private void recordSuccess(String subject, String address) {
        try {
            riskLimiter.recordSuccess(subject, address);
        } catch (LoginRiskUnavailableException exception) {
            throw unavailable();
        }
    }

    private static ApiException rejected(
            HttpStatus status,
            String code
    ) {
        return new ApiException(
                status,
                code,
                "Reauthentication rejected",
                "The reauthentication request could not be accepted."
        );
    }

    private static ApiException unavailable() {
        return rejected(
                HttpStatus.SERVICE_UNAVAILABLE,
                "REAUTH_RISK_UNAVAILABLE"
        );
    }
}
