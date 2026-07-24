package com.storyplatform.monetization.application;

import com.storyplatform.monetization.domain.WalletAccount;

import java.time.Instant;

public interface WalletOperations {

    WalletBalance balance(String userId);

    WalletBalance open(
            WalletAccount.OwnerType ownerType,
            String ownerId
    );

    record WalletBalance(
            String currency,
            long availableXu,
            long reservedXu,
            long version,
            Instant asOf
    ) {
    }
}
