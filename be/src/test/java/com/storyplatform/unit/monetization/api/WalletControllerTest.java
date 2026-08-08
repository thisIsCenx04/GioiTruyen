package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api.WalletController;
import com.storyplatform.monetization.application.WalletOperations;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletControllerTest {

    @Test
    void returnsCallerBalanceWithPrivateCachePolicy() {
        WalletOperations wallets = mock(WalletOperations.class);
        Jwt jwt = new Jwt(
                "token",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", "user")
        );
        var balance = new WalletOperations.WalletBalance(
                "XU", 100, 20, 3, Instant.EPOCH
        );
        when(wallets.balance("user")).thenReturn(balance);

        var response = new WalletController(wallets).balance(jwt);

        assertThat(response.getBody()).isEqualTo(balance);
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
    }
}
