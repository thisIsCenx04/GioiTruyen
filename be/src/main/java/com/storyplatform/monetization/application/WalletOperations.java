package com.storyplatform.monetization.application;

import com.storyplatform.monetization.domain.WalletAccount;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public interface WalletOperations {

    static String accountId(
            WalletAccount.OwnerType ownerType,
            String ownerId
    ) {
        return UUID.nameUUIDFromBytes(
                ("gioitruyen:XU:" + ownerType + ":" + ownerId)
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

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
