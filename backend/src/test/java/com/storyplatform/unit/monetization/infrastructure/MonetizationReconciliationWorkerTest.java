package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application
        .MonetizationReconciliationOperations;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationGateway;
import com.storyplatform.monetization.infrastructure
        .MonetizationReconciliationWorker;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonetizationReconciliationWorkerTest {

    @Test
    void fetchesPreviousUtcDayOutsideReconciliationTransaction() {
        var gateway = mock(MonetizationReconciliationGateway.class);
        var operations = mock(MonetizationReconciliationOperations.class);
        Instant from = Instant.parse("2026-07-24T00:00:00Z");
        Instant to = Instant.parse("2026-07-25T00:00:00Z");
        var statement = new MonetizationReconciliationGateway.Statement(
                "bank-provider",
                List.of()
        );
        when(gateway.fetch(from, to)).thenReturn(statement);

        new MonetizationReconciliationWorker(
                gateway,
                operations,
                Clock.fixed(
                        Instant.parse("2026-07-25T12:00:00Z"),
                        ZoneOffset.UTC
                )
        ).reconcilePreviousUtcDay();

        verify(operations).reconcile(from, to, statement);
    }
}
