package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.WalletAccount;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoWalletAccountDocument.COLLECTION)
public record MongoWalletAccountDocument(
        @Id String id,
        String ownerType,
        String ownerId,
        String normalSide,
        String status,
        String currency,
        Instant createdAt
) {
    public static final String COLLECTION = "wallet_accounts";

    static MongoWalletAccountDocument from(WalletAccount account) {
        return new MongoWalletAccountDocument(
                account.id(),
                account.ownerType().name(),
                account.ownerId(),
                account.normalSide().name(),
                account.status().name(),
                account.currency(),
                account.createdAt()
        );
    }

    WalletAccount toDomain() {
        return new WalletAccount(
                id,
                WalletAccount.OwnerType.valueOf(ownerType),
                ownerId,
                LedgerEntry.Side.valueOf(normalSide),
                WalletAccount.Status.valueOf(status),
                currency,
                createdAt
        );
    }
}
