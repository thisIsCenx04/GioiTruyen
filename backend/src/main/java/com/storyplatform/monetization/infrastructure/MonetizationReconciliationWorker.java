package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .MonetizationReconciliationOperations;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationGateway;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

public final class MonetizationReconciliationWorker {

    private final MonetizationReconciliationGateway gateway;
    private final MonetizationReconciliationOperations operations;
    private final Clock clock;

    public MonetizationReconciliationWorker(
            MonetizationReconciliationGateway gateway,
            MonetizationReconciliationOperations operations,
            Clock clock
    ) {
        this.gateway = Objects.requireNonNull(gateway);
        this.operations = Objects.requireNonNull(operations);
        this.clock = Objects.requireNonNull(clock);
    }

    @Scheduled(
            cron = "${app.monetization.reconciliation.cron:"
                    + "0 15 1 * * *}",
            zone = "UTC"
    )
    public void reconcilePreviousUtcDay() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        Instant from = today.minusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        Instant to = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        var statement = gateway.fetch(from, to);
        operations.reconcile(from, to, statement);
    }
}
