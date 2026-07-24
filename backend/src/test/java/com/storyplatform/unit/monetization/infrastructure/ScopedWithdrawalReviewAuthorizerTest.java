package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.identity.application.contract
        .ReauthenticationVerifier;
import com.storyplatform.monetization.infrastructure
        .ScopedWithdrawalReviewAuthorizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScopedWithdrawalReviewAuthorizerTest {

    @Test
    void consumesOnlyWithdrawalScopedGrant() {
        ReauthenticationVerifier verifier =
                mock(ReauthenticationVerifier.class);
        when(verifier.consume(
                "grant",
                "reviewer",
                "WITHDRAWAL_APPROVAL",
                "withdrawal",
                "withdrawal-id"
        )).thenReturn(true);

        assertThat(new ScopedWithdrawalReviewAuthorizer(verifier)
                .consume("reviewer", "grant", "withdrawal-id"))
                .isTrue();
    }
}
