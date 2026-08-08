package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.identity.application.contract
        .ReauthenticationVerifier;
import com.storyplatform.monetization.infrastructure
        .ScopedManualTopupAuthorizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScopedManualTopupAuthorizerTest {

    @Test
    void consumesAnExactSingleUseTopupApprovalScope() {
        ReauthenticationVerifier verifier =
                mock(ReauthenticationVerifier.class);
        when(verifier.consume(
                "grant",
                "admin",
                "TOPUP_MANUAL_APPROVAL",
                "topup_request",
                "topup-1"
        )).thenReturn(true);
        var authorizer = new ScopedManualTopupAuthorizer(verifier);

        assertThat(authorizer.consume(
                "admin", "grant", "topup-1"
        )).isTrue();
        verify(verifier).consume(
                "grant",
                "admin",
                "TOPUP_MANUAL_APPROVAL",
                "topup_request",
                "topup-1"
        );
    }
}
