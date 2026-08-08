package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.WalletAccount;

import java.time.Instant;
import java.util.Optional;

public interface WalletRepository {

    Optional<AccountBalance> find(
            WalletAccount.OwnerType ownerType,
            String ownerId
    );

    AccountBalance insert(WalletAccount account);

    void project(LedgerTransaction transaction, Instant updatedAt);

    record AccountBalance(
            WalletAccount account,
            long availableXu,
            long reservedXu,
            long version,
            Instant updatedAt
    ) {
        public AccountBalance {
            if (availableXu < 0 || reservedXu < 0 || version < 0) {
                throw new IllegalArgumentException(
                        "Wallet balances and version cannot be negative."
                );
            }
        }
    }
}
