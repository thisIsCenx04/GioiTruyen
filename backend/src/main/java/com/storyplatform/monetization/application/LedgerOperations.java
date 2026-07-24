package com.storyplatform.monetization.application;

import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;

import java.util.List;

public interface LedgerOperations {

    Posting post(Command command);

    record Command(
            LedgerTransaction.Type type,
            String referenceType,
            String referenceId,
            List<LedgerEntry> entries,
            String idempotencyKeyHash,
            String correlationId,
            String actorId,
            String teamId
    ) {
        public Command {
            entries = List.copyOf(entries);
        }
    }

    record Posting(LedgerTransaction transaction, boolean replayed) {
    }
}
