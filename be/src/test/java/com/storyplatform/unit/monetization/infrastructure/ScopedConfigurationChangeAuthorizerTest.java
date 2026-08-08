package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.identity.application.contract
        .ReauthenticationVerifier;
import com.storyplatform.monetization.infrastructure
        .ScopedConfigurationChangeAuthorizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScopedConfigurationChangeAuthorizerTest {

    @Test
    void consumesOnlyTheBoundSystemConfigurationGrant() {
        ReauthenticationVerifier reauthentication =
                mock(ReauthenticationVerifier.class);
        when(reauthentication.consume(
                "grant",
                "admin",
                "SYSTEM_CONFIG_CHANGE",
                "configuration",
                "topup-discount"
        )).thenReturn(true);
        var authorizer = new ScopedConfigurationChangeAuthorizer(
                reauthentication
        );

        assertThat(authorizer.consume("admin", "grant")).isTrue();
        verify(reauthentication).consume(
                "grant",
                "admin",
                "SYSTEM_CONFIG_CHANGE",
                "configuration",
                "topup-discount"
        );
    }
}
