package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application
        .MonetizationReconciliationException;
import com.storyplatform.monetization.application
        .MonetizationReconciliationService;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationGateway;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationRepository;
import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonetizationReconciliationServiceTest {

    private static final Instant FROM =
            Instant.parse("2026-07-24T00:00:00Z");
    private static final Instant TO =
            Instant.parse("2026-07-25T00:00:00Z");
    private static final String SUBJECT =
            "10000000-0000-4000-8000-000000000001";
    private final MonetizationReconciliationRepository repository =
            mock(MonetizationReconciliationRepository.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final AtomicLong sequence = new AtomicLong();

    @Test
    void matchesFixtureAndCreatesOneCaseForEveryMismatchKind() {
        when(repository.findRun("bank-provider", FROM, TO))
                .thenReturn(Optional.empty());
        when(repository.findLocalEntries(
                "bank-provider", FROM, TO
        )).thenReturn(List.of(
                local("match-001", 100, status("PAID"), "ledger"),
                local("local-only", 100, status("PAID"), "ledger"),
                local("amount-001", 100, status("PAID"), "ledger"),
                local("status-001", 100, status("PENDING"), null),
                local("ledger-001", 100, status("PAID"), null),
                local(
                        MonetizationReconciliationCase.SubjectType.WITHDRAWAL,
                        "failed-ledger",
                        100,
                        status("FAILED"),
                        null
                )
        ));
        var statement = statement(List.of(
                provider("match-001", 100, status("PAID")),
                provider("provider-only", 100, status("PAID")),
                provider("amount-001", 101, status("PAID")),
                provider("status-001", 100, status("FAILED")),
                provider("ledger-001", 100, status("PAID")),
                provider(
                        MonetizationReconciliationCase.SubjectType.WITHDRAWAL,
                        "failed-ledger",
                        100,
                        status("FAILED")
                )
        ));

        var summary = service().reconcile(FROM, TO, statement);

        assertThat(summary.matched()).isOne();
        assertThat(summary.mismatched()).isEqualTo(6);
        assertThat(summary.replayed()).isFalse();
        var cases = ArgumentCaptor.forClass(
                MonetizationReconciliationCase.class
        );
        verify(repository, org.mockito.Mockito.times(6))
                .insertCase(cases.capture());
        assertThat(cases.getAllValues())
                .extracting(MonetizationReconciliationCase::mismatch)
                .containsExactlyInAnyOrder(
                        MonetizationReconciliationCase.Mismatch
                                .MISSING_PROVIDER_EVIDENCE,
                        MonetizationReconciliationCase.Mismatch
                                .MISSING_LOCAL_EVIDENCE,
                        MonetizationReconciliationCase.Mismatch
                                .AMOUNT_MISMATCH,
                        MonetizationReconciliationCase.Mismatch
                                .STATUS_MISMATCH,
                        MonetizationReconciliationCase.Mismatch
                                .MISSING_LEDGER_POSTING,
                        MonetizationReconciliationCase.Mismatch
                                .MISSING_LEDGER_POSTING
                );
        verify(repository).complete(
                any(), any(), any()
        );
        verify(outbox).append(any());
    }

    @Test
    void replaysCompletedWindowOnlyForIdenticalStatement() {
        var statement = statement(List.of(
                provider("match-001", 100, status("PAID"))
        ));
        when(repository.findRun("bank-provider", FROM, TO))
                .thenReturn(Optional.empty());
        when(repository.findLocalEntries(
                "bank-provider", FROM, TO
        )).thenReturn(List.of(
                local("match-001", 100, status("PAID"), "ledger")
        ));
        var first = service().reconcile(FROM, TO, statement);
        var run = ArgumentCaptor.forClass(
                MonetizationReconciliationRepository.Run.class
        );
        verify(repository).insertRun(run.capture());
        when(repository.findRun("bank-provider", FROM, TO))
                .thenReturn(Optional.of(
                        new MonetizationReconciliationRepository.Run(
                                run.getValue().id(),
                                run.getValue().provider(),
                                FROM,
                                TO,
                                run.getValue().statementHash(),
                                MonetizationReconciliationRepository.State
                                        .COMPLETED,
                                first,
                                FROM,
                                TO
                        )
                ));

        assertThat(service().reconcile(FROM, TO, statement).replayed())
                .isTrue();
        assertThatThrownBy(() -> service().reconcile(
                FROM,
                TO,
                statement(List.of(
                        provider("match-001", 101, status("PAID"))
                ))
        )).isInstanceOf(MonetizationReconciliationException.class);
    }

    @Test
    void rejectsDuplicateProviderReferencesAndInvalidWindow() {
        when(repository.findRun("bank-provider", FROM, TO))
                .thenReturn(Optional.empty());
        var duplicate = provider("duplicate-001", 100, status("PAID"));

        assertThatThrownBy(() -> service().reconcile(
                FROM,
                TO,
                statement(List.of(duplicate, duplicate))
        )).isInstanceOf(MonetizationReconciliationException.class);
        assertThatThrownBy(() -> service().reconcile(
                TO,
                FROM,
                statement(List.of())
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private MonetizationReconciliationService service() {
        return new MonetizationReconciliationService(
                repository,
                outbox,
                Clock.fixed(TO, ZoneOffset.UTC),
                () -> new UUID(0, sequence.incrementAndGet())
        );
    }

    private static MonetizationReconciliationGateway.Statement statement(
            List<MonetizationReconciliationGateway.Entry> entries
    ) {
        return new MonetizationReconciliationGateway.Statement(
                "bank-provider",
                entries
        );
    }

    private static MonetizationReconciliationGateway.Entry provider(
            String reference,
            long amount,
            MonetizationReconciliationGateway.Status status
    ) {
        return provider(
                MonetizationReconciliationCase.SubjectType.TOPUP,
                reference,
                amount,
                status
        );
    }

    private static MonetizationReconciliationGateway.Entry provider(
            MonetizationReconciliationCase.SubjectType type,
            String reference,
            long amount,
            MonetizationReconciliationGateway.Status status
    ) {
        return new MonetizationReconciliationGateway.Entry(
                type,
                reference,
                amount,
                status,
                FROM.plusSeconds(60)
        );
    }

    private static MonetizationReconciliationRepository.LocalEntry local(
            String reference,
            long amount,
            MonetizationReconciliationGateway.Status status,
            String ledger
    ) {
        return local(
                MonetizationReconciliationCase.SubjectType.TOPUP,
                reference,
                amount,
                status,
                ledger
        );
    }

    private static MonetizationReconciliationRepository.LocalEntry local(
            MonetizationReconciliationCase.SubjectType type,
            String reference,
            long amount,
            MonetizationReconciliationGateway.Status status,
            String ledger
    ) {
        return new MonetizationReconciliationRepository.LocalEntry(
                type,
                SUBJECT,
                reference,
                amount,
                status,
                ledger,
                FROM.plusSeconds(60)
        );
    }

    private static MonetizationReconciliationGateway.Status status(
            String value
    ) {
        return MonetizationReconciliationGateway.Status.valueOf(value);
    }
}
