package com.storyplatform.identity.infrastructure;

import com.storyplatform.identity.application.EmailVerificationOutcome;
import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.RegisterUserCommand;
import com.storyplatform.identity.application.RegisterUserUseCase;
import com.storyplatform.identity.application.RegistrationOutcome;
import com.storyplatform.identity.application.VerifyEmailUseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalIdentityService implements IdentityService {

    private final RegisterUserUseCase registerUser;
    private final VerifyEmailUseCase verifyEmail;

    public TransactionalIdentityService(
            RegisterUserUseCase registerUser,
            VerifyEmailUseCase verifyEmail
    ) {
        this.registerUser = Objects.requireNonNull(
                registerUser,
                "registerUser"
        );
        this.verifyEmail = Objects.requireNonNull(verifyEmail, "verifyEmail");
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
}
