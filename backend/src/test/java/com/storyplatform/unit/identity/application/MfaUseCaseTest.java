package com.storyplatform.unit.identity.application;

import com.storyplatform.identity.application.MfaUseCase;
import com.storyplatform.identity.application.port.MfaCryptography;
import com.storyplatform.identity.application.port.MfaFactorRepository;
import com.storyplatform.identity.application.port.RefreshTokenFamilyRepository;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.domain.GlobalRole;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.domain.UserState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MfaUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final MfaFactorRepository factors =
            mock(MfaFactorRepository.class);
    private final MfaCryptography cryptography =
            mock(MfaCryptography.class);
    private final UserAccountRepository users =
            mock(UserAccountRepository.class);
    private final RefreshTokenFamilyRepository sessions =
            mock(RefreshTokenFamilyRepository.class);
    private final MfaUseCase useCase = new MfaUseCase(
            factors,
            cryptography,
            users,
            sessions,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void ordinaryUserDoesNotRequireMfa() {
        assertThat(useCase.authenticate(
                account(GlobalRole.USER),
                null
        )).isEqualTo(MfaUseCase.AuthenticationResult.VERIFIED);
        verifyNoInteractions(factors, cryptography);
    }

    @Test
    void privilegedUserDoesNotRequireMfa() {
        UserAccount admin = account(GlobalRole.ADMIN);

        assertThat(useCase.authenticate(admin, null)).isEqualTo(
                MfaUseCase.AuthenticationResult.VERIFIED
        );
        verifyNoInteractions(factors, cryptography);
    }

    @Test
    void configuredFactorIsNotRequiredForModeratorLogin() {
        UserAccount moderator = account(GlobalRole.MODERATOR);

        assertThat(useCase.authenticate(moderator, null))
                .isEqualTo(MfaUseCase.AuthenticationResult.VERIFIED);
        verifyNoInteractions(factors, cryptography);
    }

    @Test
    void enrollmentActivatesAndInvalidatesExistingSessions() {
        when(cryptography.generateSecret()).thenReturn(
                new MfaCryptography.EnrollmentSecret("BASE32", "protected")
        );
        when(factors.savePending("user-1", "protected", NOW))
                .thenReturn(true);
        assertThat(useCase.beginEnrollment("user-1")
                .provisioningSecret()).isEqualTo("BASE32");
        verify(factors).savePending("user-1", "protected", NOW);

        when(factors.findByUserId("user-1"))
                .thenReturn(Optional.of(factor(false)));
        when(cryptography.verifyTotp("protected", "123456", NOW))
                .thenReturn(true);
        when(cryptography.generateRecoveryCodes()).thenReturn(List.of(
                new MfaCryptography.RecoveryCode("raw-1", "hash-1")
        ));
        when(factors.activate(
                "user-1",
                List.of("hash-1"),
                NOW
        )).thenReturn(true);
        when(users.incrementSecurityVersion("user-1", NOW))
                .thenReturn(true);

        MfaUseCase.EnrollmentResult result =
                useCase.verifyEnrollment("user-1", "123456");
        assertThat(result.activated()).isTrue();
        assertThat(result.recoveryCodes()).containsExactly("raw-1");
        verify(users).incrementSecurityVersion("user-1", NOW);
        verify(sessions).revokeAllOwned(
                "user-1",
                NOW,
                "USER_REVOKED"
        );
    }

    @Test
    void enrollmentRejectsMissingOrInvalidPendingFactor() {
        when(factors.findByUserId("user-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(factor(false)));
        when(cryptography.verifyTotp("protected", "000000", NOW))
                .thenReturn(false);

        assertThat(useCase.verifyEnrollment("user-1", "000000")
                .activated()).isFalse();
        assertThat(useCase.verifyEnrollment("user-1", "000000")
                .activated()).isFalse();
        verifyNoInteractions(users, sessions);
    }

    @Test
    void enabledFactorCannotBeReplacedWithoutReauthentication() {
        when(factors.findByUserId("user-1"))
                .thenReturn(Optional.of(factor(true)));

        assertThat(useCase.beginEnrollment("user-1").created())
                .isFalse();
        verifyNoInteractions(cryptography);
    }

    @Test
    void concurrentActivationPreventsPendingFactorOverwrite() {
        when(cryptography.generateSecret()).thenReturn(
                new MfaCryptography.EnrollmentSecret("BASE32", "protected")
        );
        when(factors.savePending("user-1", "protected", NOW))
                .thenReturn(false);

        assertThat(useCase.beginEnrollment("user-1").created())
                .isFalse();
    }

    private static MfaFactorRepository.Factor factor(boolean enabled) {
        return new MfaFactorRepository.Factor(
                "user-1",
                "protected",
                enabled
        );
    }

    private static UserAccount account(GlobalRole role) {
        return new UserAccount(
                "user-1",
                "reader@example.com",
                "$argon2id$hash",
                Set.of(role),
                UserState.ACTIVE,
                1,
                "2026-07-24",
                NOW,
                NOW,
                NOW,
                0
        );
    }
}
