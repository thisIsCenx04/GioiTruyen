package com.storyplatform.identity.infrastructure;

import com.storyplatform.identity.application.EmailVerificationOutcome;
import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.LoginCommand;
import com.storyplatform.identity.application.LoginOutcome;
import com.storyplatform.identity.application.LoginUseCase;
import com.storyplatform.identity.application.RefreshSessionOutcome;
import com.storyplatform.identity.application.RefreshSessionUseCase;
import com.storyplatform.identity.application.SessionManagementUseCase;
import com.storyplatform.identity.application.SessionView;
import com.storyplatform.identity.application.RegisterUserCommand;
import com.storyplatform.identity.application.RegisterUserUseCase;
import com.storyplatform.identity.application.RegistrationOutcome;
import com.storyplatform.identity.application.VerifyEmailUseCase;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Objects;

public class TransactionalIdentityService implements IdentityService {

    private final RegisterUserUseCase registerUser;
    private final VerifyEmailUseCase verifyEmail;
    private final LoginUseCase login;
    private final RefreshSessionUseCase refreshSession;
    private final SessionManagementUseCase sessions;

    public TransactionalIdentityService(
            RegisterUserUseCase registerUser,
            VerifyEmailUseCase verifyEmail,
            LoginUseCase login,
            RefreshSessionUseCase refreshSession,
            SessionManagementUseCase sessions
    ) {
        this.registerUser = Objects.requireNonNull(
                registerUser,
                "registerUser"
        );
        this.verifyEmail = Objects.requireNonNull(verifyEmail, "verifyEmail");
        this.login = Objects.requireNonNull(login, "login");
        this.refreshSession = Objects.requireNonNull(
                refreshSession,
                "refreshSession"
        );
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @Override
    @Transactional
    public RegistrationOutcome register(RegisterUserCommand command) {
        return registerUser.register(command);
    }

    @Override
    @Transactional
    public EmailVerificationOutcome verifyEmail(String token) {
        return verifyEmail.verify(token);
    }

    @Override
    @Transactional
    public LoginOutcome login(LoginCommand command) {
        return login.login(command);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefreshSessionOutcome refreshSession(String refreshToken) {
        return refreshSession.refresh(refreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<SessionView> listSessions(
            String userId,
            String currentSessionId
    ) {
        return sessions.list(userId, currentSessionId);
    }

    @Override
    @Transactional
    public void logout(String userId, String currentSessionId) {
        sessions.revokeCurrent(userId, currentSessionId);
    }

    @Override
    @Transactional
    public void revokeSession(String userId, String sessionId) {
        sessions.revokeSpecific(userId, sessionId);
    }

    @Override
    @Transactional
    public void revokeAllSessions(String userId) {
        sessions.revokeAll(userId);
    }
}
