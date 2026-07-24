package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.WalletOperations;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public final class WalletController {

    private final WalletOperations wallets;

    public WalletController(WalletOperations wallets) {
        this.wallets = Objects.requireNonNull(wallets);
    }

    @GetMapping("/wallets/me")
    public ResponseEntity<WalletOperations.WalletBalance> balance(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(wallets.balance(jwt.getSubject()));
    }
}
