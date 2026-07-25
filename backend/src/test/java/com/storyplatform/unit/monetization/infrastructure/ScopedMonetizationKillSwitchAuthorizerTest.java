package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.identity.application.contract
        .ReauthenticationVerifier;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;
import com.storyplatform.monetization.infrastructure
        .ScopedMonetizationKillSwitchAuthorizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScopedMonetizationKillSwitchAuthorizerTest {

    @Test
    void consumesOperationScopedReauthenticationGrant() {
        ReauthenticationVerifier verifier =
                mock(ReauthenticationVerifier.class);
        when(verifier.consume(
                "reauth",
                "actor",
                "MONETIZATION_KILL_SWITCH",
                "monetization-operation",
                "withdrawal_payout"
        )).thenReturn(true);
        var authorizer =
                new ScopedMonetizationKillSwitchAuthorizer(verifier);

        assertThat(authorizer.consume(
                "actor",
                "reauth",
                MonetizationKillSwitch.Operation.WITHDRAWAL_PAYOUT
        )).isTrue();
        verify(verifier).consume(
                "reauth",
                "actor",
                "MONETIZATION_KILL_SWITCH",
                "monetization-operation",
                "withdrawal_payout"
        );
    }
}
