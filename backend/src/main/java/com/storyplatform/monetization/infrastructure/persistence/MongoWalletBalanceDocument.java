package com.storyplatform.monetization.infrastructure.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoWalletBalanceDocument.COLLECTION)
public record MongoWalletBalanceDocument(
        @Id String accountId,
        long availableXu,
        long reservedXu,
        long version,
        Instant updatedAt
) {
    public static final String COLLECTION = "wallet_balances";
}
