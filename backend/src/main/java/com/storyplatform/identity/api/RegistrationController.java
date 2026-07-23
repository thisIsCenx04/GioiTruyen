package com.storyplatform.identity.api;

import com.storyplatform.identity.application.RegisterUserCommand;
import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.RegistrationOutcome;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.api.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/auth")
public class RegistrationController {

    private final IdentityService identityService;

    public RegistrationController(IdentityService identityService) {
        this.identityService = Objects.requireNonNull(
                identityService,
                "identityService"
        );
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest servletRequest
    ) {
        RegistrationOutcome outcome = identityService.register(
                new RegisterUserCommand(
                        request.email(),
                        request.password(),
                        request.consentVersion(),
                        CorrelationId.from(servletRequest)
                )
        );

        return switch (outcome) {
            case ACCEPTED -> ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(RegistrationResponse.pendingVerification());
            case INVALID_EMAIL -> throw invalidRequest(
                    "EMAIL_INVALID",
                    "The email address is invalid."
            );
            case WEAK_PASSWORD -> throw invalidRequest(
                    "PASSWORD_REJECTED",
                    "The password does not meet the registration policy."
            );
            case CONSENT_VERSION_REJECTED -> throw invalidRequest(
                    "CONSENT_VERSION_REJECTED",
                    "The current terms must be accepted."
            );
        };
    }

    private static ApiException invalidRequest(
            String code,
            String detail
    ) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                code,
                "Registration request rejected",
                detail
        );
    }
}
