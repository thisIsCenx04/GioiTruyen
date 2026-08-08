package com.storyplatform.identity.application;

import com.storyplatform.identity.application.port.EmailVerificationRepository;
import com.storyplatform.identity.application.port.VerificationTokenCodec;
import com.storyplatform.identity.application.port.UserAccountRepository;

import java.time.Clock;
import java.util.Objects;

public final class VerifyEmailUseCase {

    private final EmailVerificationRepository verificationRepository;
    private final UserAccountRepository userRepository;
    private final VerificationTokenCodec tokenCodec;
    private final Clock clock;

    public VerifyEmailUseCase(
            EmailVerificationRepository verificationRepository,
            UserAccountRepository userRepository,
            VerificationTokenCodec tokenCodec,
            Clock clock
    ) {
        this.verificationRepository = Objects.requireNonNull(
                verificationRepository,
                "verificationRepository"
        );
        this.userRepository = Objects.requireNonNull(
                userRepository,
                "userRepository"
        );
        this.tokenCodec = Objects.requireNonNull(tokenCodec, "tokenCodec");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public EmailVerificationOutcome verify(String rawToken) {
        if (!tokenCodec.isWellFormed(rawToken)) {
            return EmailVerificationOutcome.INVALID_OR_EXPIRED;
        }

        return verificationRepository.consume(
                        tokenCodec.hash(rawToken),
                        clock.instant()
                )
                .map(verification -> activate(verification.userId()))
                .orElse(EmailVerificationOutcome.INVALID_OR_EXPIRED);
    }

    private EmailVerificationOutcome activate(String userId) {
        if (!userRepository.activatePending(userId, clock.instant())) {
            throw new IllegalStateException(
                    "Verification consumed for a non-pending account"
            );
        }
        return EmailVerificationOutcome.VERIFIED;
    }
}
