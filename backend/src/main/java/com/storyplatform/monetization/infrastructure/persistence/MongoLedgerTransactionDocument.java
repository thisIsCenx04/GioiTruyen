package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = MongoLedgerTransactionDocument.COLLECTION)
public record MongoLedgerTransactionDocument(
        @Id String id,
        String type,
        String referenceType,
        String referenceId,
        String state,
        List<EntryDocument> entries,
        String idempotencyKeyHash,
        String compensatesTransactionId,
        Instant createdAt
) {
    public static final String COLLECTION = "ledger_transactions";

    static MongoLedgerTransactionDocument from(
            LedgerTransaction transaction
    ) {
        return new MongoLedgerTransactionDocument(
                transaction.id(),
                transaction.type().name(),
                transaction.referenceType(),
                transaction.referenceId(),
                transaction.state().name(),
                transaction.entries().stream()
                        .map(EntryDocument::from)
                        .toList(),
                transaction.idempotencyKeyHash(),
                transaction.compensatesTransactionId(),
                transaction.createdAt()
        );
    }

    LedgerTransaction toDomain() {
        return new LedgerTransaction(
                id,
                LedgerTransaction.Type.valueOf(type),
                referenceType,
                referenceId,
                LedgerTransaction.State.valueOf(state),
                entries.stream().map(EntryDocument::toDomain).toList(),
                idempotencyKeyHash,
                compensatesTransactionId,
                createdAt
        );
    }

    public record EntryDocument(
            String accountId,
            String side,
            String bucket,
            long amountXu
    ) {
        static EntryDocument from(LedgerEntry entry) {
            return new EntryDocument(
                    entry.accountId(),
                    entry.side().name(),
                    entry.bucket().name(),
                    entry.amountXu()
            );
        }

        LedgerEntry toDomain() {
            return new LedgerEntry(
                    accountId,
                    LedgerEntry.Side.valueOf(side),
                    bucket == null
                            ? LedgerEntry.Bucket.AVAILABLE
                            : LedgerEntry.Bucket.valueOf(bucket),
                    amountXu
            );
        }
    }
}
