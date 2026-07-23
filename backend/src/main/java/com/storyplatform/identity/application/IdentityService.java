package com.storyplatform.identity.application;

public interface IdentityService {

    RegistrationOutcome register(RegisterUserCommand command);

    EmailVerificationOutcome verifyEmail(String token);

    LoginOutcome login(LoginCommand command);

    RefreshSessionOutcome refreshSession(String refreshToken);
}
