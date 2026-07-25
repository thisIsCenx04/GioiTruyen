package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .MonetizationReconciliationRepository;
import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class MonetizationReconciliationResolutionService
        implements MonetizationReconciliationResolutionOperations {

    private final MonetizationReconciliationRepository repository;
    private final OutboxAppender outbox;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public MonetizationReconciliationResolutionService(
            MonetizationReconciliationRepository repository,
            OutboxAppender outbox,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public MonetizationReconciliationCase resolve(
            String caseId,
            String actorId,
            String reason,
            MonetizationReconciliationCase.ResolutionAction action,
            String compensationTransactionId
    ) {
        var existing = repository.findCase(caseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Reconciliation case was not found."
                ));
        if (action
                == MonetizationReconciliationCase.ResolutionAction
                        .COMPENSATING_LEDGER_POSTED
                && (compensationTransactionId == null
                || !repository.hasPostedLedgerTransaction(
                        compensationTransactionId
                ))) {
            throw new IllegalArgumentException(
                    "Posted compensation evidence is required."
            );
        }
        var resolved = existing.resolve(
                actorId,
                reason,
                action,
                compensationTransactionId,
                clock.instant()
        );
        if (!repository.resolveCase(existing, resolved)) {
            throw new MonetizationReconciliationException(
                    "Reconciliation case changed concurrently."
            );
        }
        outbox.append(new IntegrationEvent(
                ids.get(),
                "monetization.reconciliation.case.resolved",
                1,
                resolved.resolvedAt(),
                resolved.runId(),
                "monetization_reconciliation_case",
                resolved.id(),
                actorId,
                null,
                new Resolved(
                        resolved.mismatch().name(),
                        action.name(),
                        compensationTransactionId
                )
        ));
        return resolved;
    }

    public record Resolved(
            String mismatch,
            String action,
            String compensationTransactionId
    ) {
    }
}
