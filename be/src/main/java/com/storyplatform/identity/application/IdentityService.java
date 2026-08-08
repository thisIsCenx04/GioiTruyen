package com.storyplatform.identity.application;

public interface IdentityService {

    RegistrationOutcome register(RegisterUserCommand command);

    EmailVerificationOutcome verifyEmail(String token);

    LoginOutcome login(LoginCommand command);

    RefreshSessionOutcome refreshSession(String refreshToken);

    java.util.List<SessionView> listSessions(
            String userId,
            String currentSessionId
    );

    void logout(String userId, String currentSessionId);

    void revokeSession(String userId, String sessionId);

    void revokeAllSessions(String userId);

    void requestPasswordReset(String email, String correlationId);

    PasswordResetOutcome resetPassword(String token, String newPassword);

    MfaUseCase.EnrollmentChallenge beginMfaEnrollment(String userId);

    MfaUseCase.EnrollmentResult verifyMfaEnrollment(
            String userId,
            String code
    );

    ReauthenticationUseCase.IssueResult issueReauthenticationGrant(
            ReauthenticationUseCase.IssueCommand command
    );
}
