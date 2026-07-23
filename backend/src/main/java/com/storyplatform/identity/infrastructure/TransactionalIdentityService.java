package com.storyplatform.identity.infrastructure;

import com.storyplatform.identity.application.EmailVerificationOutcome;
import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.LoginCommand;
import com.storyplatform.identity.application.LoginOutcome;
import com.storyplatform.identity.application.LoginUseCase;
import com.storyplatform.identity.application.RegisterUserCommand;
import com.storyplatform.identity.application.RegisterUserUseCase;
import com.storyplatform.identity.application.RegistrationOutcome;
import com.storyplatform.identity.application.VerifyEmailUseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalIdentityService implements IdentityService {

    private final RegisterUserUseCase registerUser;
    private final VerifyEmailUseCase verifyEmail;
    private final LoginUseCase login;

    public TransactionalIdentityService(
            RegisterUserUseCase registerUser,
            VerifyEmailUseCase verifyEmail,
            LoginUseCase login
    ) {
        this.registerUser = Objects.requireNonNull(
                registerUser,
                "registerUser"
        );
        this.verifyEmail = Objects.requireNonNull(verifyEmail, "verifyEmail");
        this.login = Objects.requireNonNull(login, "login");
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
    @Transactional(readOnly = true)
    public LoginOutcome login(LoginCommand command) {
        return login.login(command);
    }
}
