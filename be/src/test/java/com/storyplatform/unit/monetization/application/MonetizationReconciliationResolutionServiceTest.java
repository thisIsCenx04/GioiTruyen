package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application
        .MonetizationReconciliationException;
import com.storyplatform.monetization.application
        .MonetizationReconciliationResolutionService;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationRepository;
import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonetizationReconciliationResolutionServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");
    private static final String CASE_ID =
            "10000000-0000-4000-8000-000000000001";
    private static final String ACTOR =
            "30000000-0000-4000-8000-000000000001";
    private static final String COMPENSATION =
            "40000000-0000-4000-8000-000000000001";
    private final MonetizationReconciliationRepository repository =
            mock(MonetizationReconciliationRepository.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);

    @Test
    void verifiesPostedCompensationBeforeResolvingAndAuditing() {
        when(repository.findCase(CASE_ID))
                .thenReturn(Optional.of(openCase()));
        when(repository.hasPostedLedgerTransaction(COMPENSATION))
                .thenReturn(true);
        when(repository.resolveCase(any(), any())).thenReturn(true);

        var resolved = service().resolve(
                CASE_ID,
                ACTOR,
                "Verified compensating ledger posting.",
                MonetizationReconciliationCase.ResolutionAction
                        .COMPENSATING_LEDGER_POSTED,
                COMPENSATION
        );

        assertThat(resolved.status())
                .isEqualTo(MonetizationReconciliationCase.Status.RESOLVED);
        assertThat(resolved.compensationTransactionId())
                .isEqualTo(COMPENSATION);
        verify(outbox).append(any());
    }

    @Test
    void rejectsMissingCompensationAndConcurrentResolution() {
        when(repository.findCase(CASE_ID))
                .thenReturn(Optional.of(openCase()));
        assertThatThrownBy(() -> service().resolve(
                CASE_ID,
                ACTOR,
                "Compensation evidence is not posted.",
                MonetizationReconciliationCase.ResolutionAction
                        .COMPENSATING_LEDGER_POSTED,
                COMPENSATION
        )).isInstanceOf(IllegalArgumentException.class);

        when(repository.resolveCase(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service().resolve(
                CASE_ID,
                ACTOR,
                "Provider corrected the immutable statement.",
                MonetizationReconciliationCase.ResolutionAction
                        .PROVIDER_CORRECTED,
                null
        )).isInstanceOf(MonetizationReconciliationException.class);
    }

    private MonetizationReconciliationResolutionService service() {
        return new MonetizationReconciliationResolutionService(
                repository,
                outbox,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> UUID.fromString(
                        "50000000-0000-4000-8000-000000000001"
                )
        );
    }

    private static MonetizationReconciliationCase openCase() {
        return new MonetizationReconciliationCase(
                CASE_ID,
                "20000000-0000-4000-8000-000000000001",
                MonetizationReconciliationCase.SubjectType.TOPUP,
                "60000000-0000-4000-8000-000000000001",
                "provider-001",
                MonetizationReconciliationCase.Mismatch.AMOUNT_MISMATCH,
                100,
                101,
                "PAID",
                "PAID",
                MonetizationReconciliationCase.RecommendedAction
                        .INVESTIGATE_PROVIDER_EVENT,
                "a".repeat(64),
                MonetizationReconciliationCase.Status.OPEN,
                NOW.minusSeconds(60),
                null,
                null,
                null,
                null,
                null
        );
    }
}
