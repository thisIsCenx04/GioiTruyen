package com.storyplatform.identity.application;

public interface IdentityService {

    RegistrationOutcome register(RegisterUserCommand command);

    EmailVerificationOutcome verifyEmail(String token);
}
