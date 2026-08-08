package com.storyplatform.unit.identity.domain;

import com.storyplatform.identity.domain.GlobalRole;
import com.storyplatform.identity.domain.UserAccount;
import com.storyplatform.identity.domain.UserState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class UserAccountTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-24T00:00:00Z"
    );

    @Test
    void pendingFactoryCreatesLeastPrivilegeAccount() {
        UserAccount account = UserAccount.pending(
                "user-1",
                "reader@example.com",
                "$argon2id$hash",
                "2026-07-24",
                NOW
        );

        assertThat(account.state())
                .isEqualTo(UserState.PENDING_EMAIL_VERIFICATION);
        assertThat(account.globalRoles()).containsExactly(GlobalRole.USER);
        assertThat(account.securityVersion()).isEqualTo(1);
        assertThat(account.version()).isZero();
        assertThat(account.createdAt()).isEqualTo(NOW);
        assertThat(account.consentAcceptedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsAccountWithoutRoleOrPositiveSecurityVersion() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new UserAccount(
                        "user-1",
                        "reader@example.com",
                        "$argon2id$hash",
                        Set.of(),
                        UserState.PENDING_EMAIL_VERIFICATION,
                        1,
                        "2026-07-24",
                        NOW,
                        NOW,
                        NOW,
                        0
                )
        );

        assertThatIllegalArgumentException().isThrownBy(() ->
                account(
                        Set.of(GlobalRole.USER),
                        0,
                        NOW,
                        NOW,
                        0
                )
        );
    }

    @Test
    void rejectsNegativeVersionAndInconsistentTimestamps() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                account(
                        Set.of(GlobalRole.USER),
                        1,
                        NOW,
                        NOW,
                        -1
                )
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
                account(
                        Set.of(GlobalRole.USER),
                        1,
                        NOW,
                        NOW.minusSeconds(1),
                        0
                )
        );
        assertThatIllegalArgumentException().isThrownBy(() ->
                account(
                        Set.of(GlobalRole.USER),
                        1,
                        NOW.plusSeconds(1),
                        NOW,
                        0
                )
        );
    }

    @Test
    void rejectsBlankIdentityFields() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new UserAccount(
                        " ",
                        "reader@example.com",
                        "$argon2id$hash",
                        Set.of(GlobalRole.USER),
                        UserState.PENDING_EMAIL_VERIFICATION,
                        1,
                        "2026-07-24",
                        NOW,
                        NOW,
                        NOW,
                        0
                )
        );
    }

    private static UserAccount account(
            Set<GlobalRole> roles,
            long securityVersion,
            Instant consentAcceptedAt,
            Instant updatedAt,
            long version
    ) {
        return new UserAccount(
                "user-1",
                "reader@example.com",
                "$argon2id$hash",
                roles,
                UserState.PENDING_EMAIL_VERIFICATION,
                securityVersion,
                "2026-07-24",
                consentAcceptedAt,
                NOW,
                updatedAt,
                version
        );
    }
}
