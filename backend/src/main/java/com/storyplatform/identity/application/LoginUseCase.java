package com.storyplatform.identity.application;

import com.storyplatform.identity.application.port.AccessTokenIssuer;
import com.storyplatform.identity.application.port.LoginRiskLimiter;
import com.storyplatform.identity.application.port.PasswordHasher;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.domain.EmailNormalizer;
import com.storyplatform.identity.domain.UserAccount;

import java.util.Objects;
import java.util.Optional;

public final class LoginUseCase {

    private final UserAccountRepository users;
    private final PasswordHasher passwords;
    private final LoginRiskLimiter riskLimiter;
    private final AccessTokenIssuer tokenIssuer;
    private final EmailNormalizer emailNormalizer;
    private final String dummyPasswordHash;

    public LoginUseCase(
            UserAccountRepository users,
            PasswordHasher passwords,
            LoginRiskLimiter riskLimiter,
            AccessTokenIssuer tokenIssuer,
            EmailNormalizer emailNormalizer,
            String dummyPasswordHash
    ) {
        this.users = Objects.requireNonNull(users, "users");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.riskLimiter = Objects.requireNonNull(
                riskLimiter,
                "riskLimiter"
        );
        this.tokenIssuer = Objects.requireNonNull(
                tokenIssuer,
                "tokenIssuer"
        );
        this.emailNormalizer = Objects.requireNonNull(
                emailNormalizer,
                "emailNormalizer"
        );
        this.dummyPasswordHash = Objects.requireNonNull(
                dummyPasswordHash,
                "dummyPasswordHash"
        );
    }

    public LoginOutcome login(LoginCommand command) {
        Objects.requireNonNull(command, "command");
        String email = normalizeSafely(command.email());
        if (!riskLimiter.allow(email, command.clientAddress())) {
            return LoginOutcome.rateLimited(
                    riskLimiter.retryAfterSeconds()
            );
        }

        Optional<UserAccount> account = users.findByEmail(email);
        String expectedHash = account.map(UserAccount::passwordHash)
                .orElse(dummyPasswordHash);
        boolean passwordMatches = passwords.matches(
                command.password(),
                expectedHash
        );
        boolean eligible = account.map(UserAccount::isActive)
                .orElse(false);
        if (!passwordMatches || !eligible) {
            riskLimiter.recordFailure(
                    email,
                    command.clientAddress()
            );
            return LoginOutcome.invalidCredentials();
        }

        UserAccount authenticated = account.orElseThrow();
        riskLimiter.recordSuccess(email, command.clientAddress());
        AccessTokenIssuer.IssuedAccessToken token =
                tokenIssuer.issue(authenticated);
        return LoginOutcome.authenticated(
                token.value(),
                token.expiresInSeconds()
        );
    }

    private String normalizeSafely(String email) {
        try {
            return emailNormalizer.normalize(email);
        } catch (RuntimeException exception) {
            return "invalid-email";
        }
    }

}
