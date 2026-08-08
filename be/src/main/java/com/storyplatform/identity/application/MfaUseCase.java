package com.storyplatform.identity.application;

import com.storyplatform.identity.application.port.MfaCryptography;
import com.storyplatform.identity.application.port.MfaFactorRepository;
import com.storyplatform.identity.application.port.RefreshTokenFamilyRepository;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.domain.UserAccount;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class MfaUseCase {

    private final MfaFactorRepository factors;
    private final MfaCryptography cryptography;
    private final UserAccountRepository users;
    private final RefreshTokenFamilyRepository sessions;
    private final Clock clock;

    public MfaUseCase(
            MfaFactorRepository factors,
            MfaCryptography cryptography,
            UserAccountRepository users,
            RefreshTokenFamilyRepository sessions,
            Clock clock
    ) {
        this.factors = Objects.requireNonNull(factors, "factors");
        this.cryptography = Objects.requireNonNull(
                cryptography,
                "cryptography"
        );
        this.users = Objects.requireNonNull(users, "users");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public EnrollmentChallenge beginEnrollment(String userId) {
        if (factors.findByUserId(userId)
                .map(MfaFactorRepository.Factor::enabled)
                .orElse(false)) {
            return new EnrollmentChallenge(false, null);
        }
        MfaCryptography.EnrollmentSecret secret =
                cryptography.generateSecret();
        boolean saved = factors.savePending(
                userId,
                secret.protectedSecret(),
                clock.instant()
        );
        if (!saved) {
            return new EnrollmentChallenge(false, null);
        }
        return new EnrollmentChallenge(
                true,
                secret.provisioningSecret()
        );
    }

    public EnrollmentResult verifyEnrollment(
            String userId,
            String code
    ) {
        Instant now = clock.instant();
        MfaFactorRepository.Factor factor =
                factors.findByUserId(userId).orElse(null);
        if (factor == null
                || factor.enabled()
                || !cryptography.verifyTotp(
                        factor.protectedSecret(),
                        code,
                        now
                )) {
            return EnrollmentResult.invalid();
        }
        List<MfaCryptography.RecoveryCode> recovery =
                cryptography.generateRecoveryCodes();
        if (!factors.activate(
                userId,
                recovery.stream().map(
                        MfaCryptography.RecoveryCode::hash
                ).toList(),
                now
        )) {
            return EnrollmentResult.invalid();
        }
        if (!users.incrementSecurityVersion(userId, now)) {
            throw new IllegalStateException(
                    "MFA activated for an unknown account"
            );
        }
        sessions.revokeAllOwned(
                userId,
                now,
                SessionManagementUseCase.USER_REVOKED_REASON
        );
        return EnrollmentResult.activated(
                recovery.stream().map(
                        MfaCryptography.RecoveryCode::rawCode
                ).toList()
        );
    }

    public AuthenticationResult authenticate(
            UserAccount account,
            String code
    ) {
        Objects.requireNonNull(account, "account");
        return AuthenticationResult.VERIFIED;
    }

    public record EnrollmentChallenge(
            boolean created,
            String provisioningSecret
    ) {
    }

    public record EnrollmentResult(
            boolean activated,
            List<String> recoveryCodes
    ) {
        static EnrollmentResult invalid() {
            return new EnrollmentResult(false, List.of());
        }

        static EnrollmentResult activated(List<String> codes) {
            return new EnrollmentResult(true, List.copyOf(codes));
        }
    }

    public enum AuthenticationResult {
        VERIFIED,
        CODE_REQUIRED,
        ENROLLMENT_REQUIRED,
        INVALID
    }
}
