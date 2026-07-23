package com.storyplatform.unit.identity.application;

import com.storyplatform.identity.application.RegisterUserCommand;
import com.storyplatform.identity.application.RegisterUserUseCase;
import com.storyplatform.identity.application.RegistrationOutcome;
import com.storyplatform.identity.application.port.PasswordHasher;
import com.storyplatform.identity.application.port.EmailVerificationIssuer;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.domain.EmailNormalizer;
import com.storyplatform.identity.domain.PasswordPolicy;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.domain.UserState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RegisterUserUseCaseTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );
    private static final String CONSENT_VERSION = "2026-07-24";
    private final CapturingRepository repository = new CapturingRepository();
    private final CountingHasher hasher = new CountingHasher();
    private final CapturingIssuer issuer = new CapturingIssuer();
    private final RegisterUserUseCase useCase = new RegisterUserUseCase(
            repository,
            hasher,
            issuer,
            () -> "user-1",
            new EmailNormalizer(),
            new PasswordPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            CONSENT_VERSION
    );

    @Test
    void createsNormalizedPendingAccountWithoutRawPassword() {
        RegistrationOutcome outcome = useCase.register(command(
                " Reader@Example.COM ",
                "correct horse battery staple",
                CONSENT_VERSION
        ));

        assertThat(outcome).isEqualTo(RegistrationOutcome.ACCEPTED);
        assertThat(repository.accounts).singleElement().satisfies(account -> {
            assertThat(account.id()).isEqualTo("user-1");
            assertThat(account.emailNormalized())
                    .isEqualTo("reader@example.com");
            assertThat(account.passwordHash())
                    .isEqualTo("$argon2id$test-hash");
            assertThat(account.passwordHash())
                    .doesNotContain("correct horse");
            assertThat(account.state())
                    .isEqualTo(UserState.PENDING_EMAIL_VERIFICATION);
            assertThat(account.acceptedConsentVersion())
                    .isEqualTo(CONSENT_VERSION);
            assertThat(account.createdAt()).isEqualTo(NOW);
        });
        assertThat(issuer.userIds).containsExactly("user-1");
    }

    @Test
    void duplicateStillHashesAndReturnsSameAcceptedOutcome() {
        repository.available = false;

        RegistrationOutcome first = useCase.register(validCommand());
        RegistrationOutcome second = useCase.register(validCommand());

        assertThat(first).isEqualTo(RegistrationOutcome.ACCEPTED);
        assertThat(second).isEqualTo(first);
        assertThat(hasher.invocations).isEqualTo(2);
        assertThat(repository.accounts).hasSize(2);
        assertThat(issuer.userIds).isEmpty();
    }

    @Test
    void rejectsInvalidInputBeforePersistence() {
        assertThat(useCase.register(command(
                "invalid",
                "correct horse battery staple",
                CONSENT_VERSION
        ))).isEqualTo(RegistrationOutcome.INVALID_EMAIL);
        assertThat(useCase.register(command(
                "reader@example.com",
                "password1234",
                CONSENT_VERSION
        ))).isEqualTo(RegistrationOutcome.WEAK_PASSWORD);
        assertThat(useCase.register(command(
                "reader@example.com",
                "correct horse battery staple",
                "2025-01-01"
        ))).isEqualTo(
                RegistrationOutcome.CONSENT_VERSION_REJECTED
        );

        assertThat(repository.accounts).isEmpty();
        assertThat(hasher.invocations).isZero();
    }

    @Test
    void rejectsMissingCurrentConsentConfiguration() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                configuredWithConsent(null)
        ).withMessage("currentConsentVersion must not be blank");
        assertThatIllegalArgumentException().isThrownBy(() ->
                configuredWithConsent(" ")
        ).withMessage("currentConsentVersion must not be blank");
    }

    private static RegisterUserCommand validCommand() {
        return command(
                "reader@example.com",
                "correct horse battery staple",
                CONSENT_VERSION
        );
    }

    private static RegisterUserCommand command(
            String email,
            String password,
            String consentVersion
    ) {
        return new RegisterUserCommand(
                email,
                password,
                consentVersion,
                "request-1"
        );
    }

    private RegisterUserUseCase configuredWithConsent(String consentVersion) {
        return new RegisterUserUseCase(
                repository,
                hasher,
                issuer,
                () -> "user-1",
                new EmailNormalizer(),
                new PasswordPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                consentVersion
        );
    }

    private static final class CapturingRepository
            implements UserAccountRepository {

        private final List<UserAccount> accounts = new ArrayList<>();
        private boolean available = true;

        @Override
        public boolean saveIfEmailAvailable(UserAccount account) {
            accounts.add(account);
            return available;
        }

        @Override
        public boolean activatePending(String userId, Instant activatedAt) {
            return false;
        }
    }

    private static final class CountingHasher implements PasswordHasher {

        private int invocations;

        @Override
        public String hash(String rawPassword) {
            invocations++;
            return "$argon2id$test-hash";
        }
    }

    private static final class CapturingIssuer
            implements EmailVerificationIssuer {

        private final List<String> userIds = new ArrayList<>();

        @Override
        public void issue(
                String userId,
                String correlationId,
                Instant issuedAt
        ) {
            userIds.add(userId);
        }
    }
}
