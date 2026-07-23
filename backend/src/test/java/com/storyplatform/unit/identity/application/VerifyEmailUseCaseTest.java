package com.storyplatform.unit.identity.application;

import com.storyplatform.identity.application.EmailVerificationOutcome;
import com.storyplatform.identity.application.VerifyEmailUseCase;
import com.storyplatform.identity.application.port.EmailVerificationRepository;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.application.port.VerificationTokenCodec;
import com.storyplatform.identity.domain.EmailVerification;
import com.storyplatform.identity.domain.UserAccount;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class VerifyEmailUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final StubVerificationRepository verifications =
            new StubVerificationRepository();
    private final StubUserRepository users = new StubUserRepository();
    private final VerifyEmailUseCase useCase = new VerifyEmailUseCase(
            verifications,
            users,
            new StubCodec(),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void consumesTokenAndActivatesPendingAccountOnce() {
        verifications.result = Optional.of(EmailVerification.pending(
                "verification-1",
                "user-1",
                "hash",
                NOW.plusSeconds(60),
                NOW.minusSeconds(60)
        ));

        assertThat(useCase.verify("valid.token"))
                .isEqualTo(EmailVerificationOutcome.VERIFIED);
        assertThat(users.activations).isEqualTo(1);

        verifications.result = Optional.empty();
        assertThat(useCase.verify("valid.token"))
                .isEqualTo(
                        EmailVerificationOutcome.INVALID_OR_EXPIRED
                );
        assertThat(users.activations).isEqualTo(1);
    }

    @Test
    void rejectsMalformedExpiredAndUnknownTokens() {
        assertThat(useCase.verify("bad"))
                .isEqualTo(
                        EmailVerificationOutcome.INVALID_OR_EXPIRED
                );
        assertThat(verifications.consumptions).isZero();

        assertThat(useCase.verify("valid.token"))
                .isEqualTo(
                        EmailVerificationOutcome.INVALID_OR_EXPIRED
                );
        assertThat(verifications.consumptions).isEqualTo(1);
    }

    @Test
    void rollsBackConceptuallyWhenAccountCannotBeActivated() {
        verifications.result = Optional.of(EmailVerification.pending(
                "verification-1",
                "user-1",
                "hash",
                NOW.plusSeconds(60),
                NOW.minusSeconds(60)
        ));
        users.activatable = false;

        assertThatIllegalStateException()
                .isThrownBy(() -> useCase.verify("valid.token"))
                .withMessage(
                        "Verification consumed for a non-pending account"
                );
    }

    private static final class StubCodec
            implements VerificationTokenCodec {

        @Override
        public IssuedVerificationToken issue(
                String userId,
                Instant expiresAt
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String tokenForDelivery(
                String verificationId,
                String userId,
                Instant expiresAt
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String hash(String rawToken) {
            return "hash";
        }

        @Override
        public boolean isWellFormed(String rawToken) {
            return rawToken != null && rawToken.startsWith("valid.");
        }
    }

    private static final class StubVerificationRepository
            implements EmailVerificationRepository {

        private Optional<EmailVerification> result = Optional.empty();
        private int consumptions;

        @Override
        public void save(EmailVerification verification) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<EmailVerification> consume(
                String tokenHash,
                Instant consumedAt
        ) {
            consumptions++;
            return result;
        }
    }

    private static final class StubUserRepository
            implements UserAccountRepository {

        private boolean activatable = true;
        private int activations;

        @Override
        public boolean saveIfEmailAvailable(UserAccount account) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean activatePending(
                String userId,
                Instant activatedAt
        ) {
            activations++;
            return activatable;
        }
    }
}
