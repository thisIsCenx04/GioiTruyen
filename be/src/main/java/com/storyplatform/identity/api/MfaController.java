package com.storyplatform.identity.api;

import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.LoginRiskUnavailableException;
import com.storyplatform.identity.application.MfaUseCase;
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

import java.util.Objects;
import java.time.Duration;

@RestController
@RequestMapping("/auth/mfa")
public class MfaController {

    private final IdentityService identityService;
    private final LoginRiskLimiter riskLimiter;

    public MfaController(
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

    @PostMapping("/challenge")
    public MfaChallengeResponse challenge(
            @AuthenticationPrincipal Jwt jwt
    ) {
        MfaUseCase.EnrollmentChallenge challenge =
                identityService.beginMfaEnrollment(jwt.getSubject());
        if (!challenge.created()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "MFA_ALREADY_ENABLED",
                    "MFA enrollment rejected",
                    "MFA is already enabled for this account."
            );
        }
        return new MfaChallengeResponse(
                challenge.provisioningSecret(),
                "SHA1",
                6,
                30
        );
    }

    @PostMapping("/verify")
    public MfaActivationResponse verify(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MfaVerificationRequest request,
            HttpServletRequest servletRequest
    ) {
        String riskSubject = "mfa:" + jwt.getSubject();
        String address = servletRequest.getRemoteAddr();
        try {
            if (!riskLimiter.allow(riskSubject, address)) {
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "MFA_RATE_LIMITED",
                        "MFA verification rejected",
                        "Please try again later.",
                        Duration.ofSeconds(
                                riskLimiter.retryAfterSeconds()
                        )
                );
            }
        } catch (LoginRiskUnavailableException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MFA_RISK_UNAVAILABLE",
                    "MFA temporarily unavailable",
                    "Please try again later."
            );
        }
        MfaUseCase.EnrollmentResult result =
                identityService.verifyMfaEnrollment(
                        jwt.getSubject(),
                        request.code()
                );
        if (!result.activated()) {
            riskLimiter.recordFailure(riskSubject, address);
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MFA_CODE_INVALID",
                    "MFA enrollment rejected",
                    "The verification code is invalid."
            );
        }
        riskLimiter.recordSuccess(riskSubject, address);
        return new MfaActivationResponse(result.recoveryCodes());
    }
}
