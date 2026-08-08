package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port.LedgerRepository;
import com.storyplatform.monetization.application.port.LedgerBalanceProjector;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class LedgerService implements LedgerOperations {

    public static final String EVENT_TYPE =
            "monetization.ledger.posted";
    private final LedgerRepository repository;
    private final OutboxAppender outbox;
    private final LedgerBalanceProjector balances;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public LedgerService(
            LedgerRepository repository,
            OutboxAppender outbox,
            LedgerBalanceProjector balances,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.outbox = Objects.requireNonNull(outbox);
        this.balances = Objects.requireNonNull(balances);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public Posting post(Command command) {
        Objects.requireNonNull(command, "command");
        var existing = repository.findByIdempotencyKeyHash(
                command.idempotencyKeyHash()
        );
        if (existing.isPresent()) {
            requireSamePosting(existing.orElseThrow(), command);
            return new Posting(existing.orElseThrow(), true);
        }
        var transaction = LedgerTransaction.post(
                ids.get().toString(),
                command.type(),
                command.referenceType(),
                command.referenceId(),
                command.entries(),
                command.idempotencyKeyHash(),
                clock.instant()
        );
        repository.insert(transaction);
        balances.project(transaction);
        outbox.append(new IntegrationEvent(
                ids.get(),
                EVENT_TYPE,
                1,
                transaction.createdAt(),
                command.correlationId(),
                "ledger_transaction",
                transaction.id(),
                command.actorId(),
                command.teamId(),
                new LedgerPosted(
                        transaction.id(),
                        transaction.type().name(),
                        transaction.referenceType(),
                        transaction.referenceId(),
                        transaction.amountXu(),
                        "XU"
                )
        ));
        return new Posting(transaction, false);
    }

    private static void requireSamePosting(
            LedgerTransaction existing,
            Command command
    ) {
        boolean matches = existing.type() == command.type()
                && existing.referenceType().equals(command.referenceType())
                && existing.referenceId().equals(command.referenceId())
                && existing.entries().equals(command.entries());
        if (!matches) {
            throw new LedgerConflictException(
                    "The idempotency key was reused for another posting."
            );
        }
    }

    public record LedgerPosted(
            String transactionId,
            String type,
            String referenceType,
            String referenceId,
            long amountXu,
            String currency
    ) {
    }
}
