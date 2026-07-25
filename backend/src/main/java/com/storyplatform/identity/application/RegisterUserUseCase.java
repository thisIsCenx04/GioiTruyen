package com.storyplatform.identity.application;

import com.storyplatform.identity.application.port.PasswordHasher;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.application.port.UserIdGenerator;
import com.storyplatform.identity.domain.EmailNormalizer;
import com.storyplatform.identity.domain.PasswordPolicy;
import com.storyplatform.identity.domain.UserAccount;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class RegisterUserUseCase {

    private final UserAccountRepository repository;
    private final PasswordHasher passwordHasher;
    private final UserIdGenerator idGenerator;
    private final EmailNormalizer emailNormalizer;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;
    private final String currentConsentVersion;

    public RegisterUserUseCase(
            UserAccountRepository repository,
            PasswordHasher passwordHasher,
            UserIdGenerator idGenerator,
            EmailNormalizer emailNormalizer,
            PasswordPolicy passwordPolicy,
            Clock clock,
            String currentConsentVersion
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.passwordHasher = Objects.requireNonNull(
                passwordHasher,
                "passwordHasher"
        );
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "idGenerator"
        );
        this.emailNormalizer = Objects.requireNonNull(
                emailNormalizer,
                "emailNormalizer"
        );
        this.passwordPolicy = Objects.requireNonNull(
                passwordPolicy,
                "passwordPolicy"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        if (currentConsentVersion == null
                || currentConsentVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "currentConsentVersion must not be blank"
            );
        }
        this.currentConsentVersion = currentConsentVersion;
    }

    public RegistrationOutcome register(RegisterUserCommand command) {
        Objects.requireNonNull(command, "command");
        String email;
        try {
            email = emailNormalizer.normalize(command.email());
        } catch (EmailNormalizer.InvalidEmailException exception) {
            return RegistrationOutcome.INVALID_EMAIL;
        }
        if (!passwordPolicy.accepts(command.password())) {
            return RegistrationOutcome.WEAK_PASSWORD;
        }
        if (!currentConsentVersion.equals(command.consentVersion())) {
            return RegistrationOutcome.CONSENT_VERSION_REJECTED;
        }

        Instant now = clock.instant();
        UserAccount account = UserAccount.active(
                idGenerator.nextId(),
                email,
                passwordHasher.hash(command.password()),
                currentConsentVersion,
                now
        );
        repository.saveIfEmailAvailable(account);
        return RegistrationOutcome.ACCEPTED;
    }
}
