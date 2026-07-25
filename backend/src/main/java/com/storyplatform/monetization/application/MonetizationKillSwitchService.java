package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .MonetizationKillSwitchAuthorizer;
import com.storyplatform.monetization.application.port
        .MonetizationKillSwitchRepository;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class MonetizationKillSwitchService
        implements MonetizationKillSwitchOperations {

    private final MonetizationKillSwitchRepository repository;
    private final MonetizationKillSwitchAuthorizer authorizer;
    private final OutboxAppender outbox;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public MonetizationKillSwitchService(
            MonetizationKillSwitchRepository repository,
            MonetizationKillSwitchAuthorizer authorizer,
            OutboxAppender outbox,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.authorizer = Objects.requireNonNull(authorizer);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public List<MonetizationKillSwitch> current() {
        return Arrays.stream(MonetizationKillSwitch.Operation.values())
                .map(this::current)
                .toList();
    }

    @Override
    public MonetizationKillSwitch update(
            String actorId,
            String reauthenticationToken,
            MonetizationKillSwitch.Operation operation,
            long expectedVersion,
            boolean engaged,
            String reason
    ) {
        if (operation == null
                || expectedVersion < 0
                || reason == null
                || reason.strip().length() < 10
                || reason.strip().length() > 500) {
            throw exception(
                    "Kill switch change is invalid.",
                    MonetizationKillSwitchException.Kind.INVALID
            );
        }
        var previous = current(operation);
        if (previous.version() != expectedVersion) {
            throw exception(
                    "Kill switch version is stale.",
                    MonetizationKillSwitchException.Kind.CONFLICT
            );
        }
        if (!authorizer.consume(
                actorId,
                reauthenticationToken,
                operation
        )) {
            throw exception(
                    "Scoped reauthentication is required.",
                    MonetizationKillSwitchException.Kind.FORBIDDEN
            );
        }
        if (previous.engaged() == engaged) {
            return previous;
        }
        Instant now = clock.instant();
        var replacement = new MonetizationKillSwitch(
                operation,
                engaged,
                Math.addExact(previous.version(), 1),
                actorId,
                now
        );
        if (!repository.save(previous, replacement, reason.strip())) {
            throw exception(
                    "Kill switch changed concurrently.",
                    MonetizationKillSwitchException.Kind.CONFLICT
            );
        }
        outbox.append(new IntegrationEvent(
                ids.get(),
                "monetization.killswitch.changed",
                1,
                now,
                ids.get().toString(),
                "monetization_kill_switch",
                operation.name(),
                actorId,
                null,
                new Changed(
                        operation.name(),
                        previous.engaged(),
                        engaged,
                        replacement.version(),
                        reason.strip()
                )
        ));
        return replacement;
    }

    private MonetizationKillSwitch current(
            MonetizationKillSwitch.Operation operation
    ) {
        return repository.find(operation).orElseGet(() ->
                new MonetizationKillSwitch(
                        operation,
                        false,
                        0,
                        "system-default",
                        Instant.EPOCH
                ));
    }

    private static MonetizationKillSwitchException exception(
            String message,
            MonetizationKillSwitchException.Kind kind
    ) {
        return new MonetizationKillSwitchException(message, kind);
    }

    public record Changed(
            String operation,
            boolean previouslyEngaged,
            boolean engaged,
            long version,
            String reason
    ) {
    }
}
