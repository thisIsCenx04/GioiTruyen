package com.storyplatform.unit.identity.application;

import com.storyplatform.identity.application.LoginCommand;
import com.storyplatform.identity.application.LoginOutcome;
import com.storyplatform.identity.application.LoginUseCase;
import com.storyplatform.identity.application.MfaUseCase;
import com.storyplatform.identity.application.port.SessionTokenIssuer;
import com.storyplatform.identity.application.port.LoginRiskLimiter;
import com.storyplatform.identity.application.port.PasswordHasher;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.domain.EmailNormalizer;
import com.storyplatform.identity.domain.GlobalRole;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.domain.UserState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginUseCaseTest {

    private final StubUsers users = new StubUsers();
    private final StubPasswords passwords = new StubPasswords();
    private final StubRiskLimiter risk = new StubRiskLimiter();
    private final LoginUseCase useCase = new LoginUseCase(
            users,
            passwords,
            risk,
            account -> new SessionTokenIssuer.IssuedSession(
                    "signed.jwt.token",
                    600,
                    "refresh-token-value"
            ),
            unrestrictedMfa(),
            new EmailNormalizer(),
            "$argon2id$dummy"
    );

    private static MfaUseCase unrestrictedMfa() {
        MfaUseCase mfa = mock(MfaUseCase.class);
        when(mfa.authenticate(any(), nullable(String.class)))
                .thenReturn(MfaUseCase.AuthenticationResult.VERIFIED);
        return mfa;
    }

    @Test
    void authenticatesActiveAccountAndIssuesAccessToken() {
        users.account = Optional.of(account(UserState.ACTIVE));
        passwords.matches = true;

        LoginOutcome outcome = useCase.login(command());

        assertThat(outcome.status())
                .isEqualTo(LoginOutcome.Status.AUTHENTICATED);
        assertThat(outcome.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(outcome.expiresInSeconds()).isEqualTo(600);
        assertThat(outcome.refreshToken())
                .isEqualTo("refresh-token-value");
        assertThat(risk.successes).isEqualTo(1);
        assertThat(risk.failures).isZero();
    }

    @Test
    void unknownWrongAndInactiveAccountsShareGenericFailurePath() {
        assertThat(useCase.login(command()).status())
                .isEqualTo(LoginOutcome.Status.INVALID_CREDENTIALS);
        assertThat(passwords.lastHash).isEqualTo("$argon2id$dummy");

        users.account = Optional.of(account(UserState.ACTIVE));
        assertThat(useCase.login(command()).status())
                .isEqualTo(LoginOutcome.Status.INVALID_CREDENTIALS);

        users.account = Optional.of(
                account(UserState.PENDING_EMAIL_VERIFICATION)
        );
        passwords.matches = true;
        assertThat(useCase.login(command()).status())
                .isEqualTo(LoginOutcome.Status.INVALID_CREDENTIALS);

        assertThat(passwords.invocations).isEqualTo(3);
        assertThat(risk.failures).isEqualTo(3);
    }

    @Test
    void rateLimitRejectsBeforeDatabaseAndArgonWork() {
        risk.allowed = false;

        assertThat(useCase.login(command()).status())
                .isEqualTo(LoginOutcome.Status.RATE_LIMITED);
        assertThat(users.lookups).isZero();
        assertThat(passwords.invocations).isZero();
    }

    @Test
    void malformedEmailStillUsesDummyHashPath() {
        assertThat(useCase.login(new LoginCommand(
                "invalid",
                "secret",
                "127.0.0.1"
        )).status()).isEqualTo(
                LoginOutcome.Status.INVALID_CREDENTIALS
        );
        assertThat(users.lastEmail).isEqualTo("invalid-email");
        assertThat(passwords.invocations).isEqualTo(1);
    }

    @Test
    void propagatesMfaEnrollmentResultWithoutIssuingSession() {
        MfaUseCase mfa = mock(MfaUseCase.class);
        when(mfa.authenticate(any(), nullable(String.class)))
                .thenReturn(
                        MfaUseCase.AuthenticationResult.ENROLLMENT_REQUIRED
                );
        LoginUseCase privilegedLogin = new LoginUseCase(
                users,
                passwords,
                risk,
                account -> {
                    throw new AssertionError(
                            "Session must not be issued before MFA"
                    );
                },
                mfa,
                new EmailNormalizer(),
                "$argon2id$dummy"
        );
        users.account = Optional.of(account(UserState.ACTIVE));
        passwords.matches = true;

        assertThat(privilegedLogin.login(command()).status())
                .isEqualTo(
                        LoginOutcome.Status.MFA_ENROLLMENT_REQUIRED
                );
    }

    private static LoginCommand command() {
        return new LoginCommand(
                " Reader@Example.com ",
                "secret",
                "127.0.0.1"
        );
    }

    private static UserAccount account(UserState state) {
        return account(state, GlobalRole.USER);
    }

    private static UserAccount account(
            UserState state,
            GlobalRole role
    ) {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new UserAccount(
                "user-1",
                "reader@example.com",
                "$argon2id$real",
                Set.of(role),
                state,
                1,
                "2026-07-24",
                now,
                now,
                now,
                0
        );
    }

    private static final class StubUsers
            implements UserAccountRepository {

        private Optional<UserAccount> account = Optional.empty();
        private int lookups;
        private String lastEmail;

        @Override
        public boolean saveIfEmailAvailable(UserAccount account) {
            return false;
        }

        @Override
        public boolean activatePending(
                String userId,
                Instant activatedAt
        ) {
            return false;
        }

        @Override
        public Optional<UserAccount> findByEmail(String emailNormalized) {
            lookups++;
            lastEmail = emailNormalized;
            return account;
        }
    }

    private static final class StubPasswords implements PasswordHasher {

        private boolean matches;
        private int invocations;
        private String lastHash;

        @Override
        public String hash(String rawPassword) {
            return "$argon2id$dummy";
        }

        @Override
        public boolean matches(
                String rawPassword,
                String encodedPassword
        ) {
            invocations++;
            lastHash = encodedPassword;
            return matches;
        }
    }

    private static final class StubRiskLimiter
            implements LoginRiskLimiter {

        private boolean allowed = true;
        private int failures;
        private int successes;

        @Override
        public boolean allow(String email, String address) {
            return allowed;
        }

        @Override
        public long retryAfterSeconds() {
            return 900;
        }

        @Override
        public void recordFailure(String email, String address) {
            failures++;
        }

        @Override
        public void recordSuccess(String email, String address) {
            successes++;
        }
    }
}
