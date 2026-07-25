package com.storyplatform.unit.monetization.domain;

import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonetizationReconciliationCaseTest {

    @Test
    void preservesImmutableHashedMismatchEvidence() {
        var value = value("provider-001", "PAID");

        assertThat(value.status())
                .isEqualTo(MonetizationReconciliationCase.Status.OPEN);
        assertThat(value.evidenceHash()).hasSize(64);
    }

    @Test
    void rejectsUnsafeReferencesStatusesAndEvidence() {
        assertThatThrownBy(() -> value("unsafe ref", "PAID"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> value("provider-001", "bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolvesOnceWithExplicitManualOutcome() {
        var resolved = value("provider-001", "PAID").resolve(
                "40000000-0000-4000-8000-000000000001",
                "Provider evidence was corrected.",
                MonetizationReconciliationCase.ResolutionAction
                        .PROVIDER_CORRECTED,
                null,
                Instant.parse("2026-07-25T01:00:00Z")
        );

        assertThat(resolved.status())
                .isEqualTo(MonetizationReconciliationCase.Status.RESOLVED);
        assertThatThrownBy(() -> resolved.resolve(
                "40000000-0000-4000-8000-000000000001",
                "Cannot resolve the same case twice.",
                MonetizationReconciliationCase.ResolutionAction
                        .NO_FINANCIAL_CHANGE,
                null,
                Instant.parse("2026-07-25T02:00:00Z")
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsEachMissingOrUnsafeCaseEvidenceField() {
        var type = MonetizationReconciliationCase.SubjectType.TOPUP;
        var mismatch =
                MonetizationReconciliationCase.Mismatch.AMOUNT_MISMATCH;
        var action = MonetizationReconciliationCase.RecommendedAction
                .INVESTIGATE_PROVIDER_EVENT;
        var status = MonetizationReconciliationCase.Status.OPEN;
        Instant created = Instant.parse("2026-07-25T00:00:00Z");
        List<org.assertj.core.api.ThrowableAssert.ThrowingCallable> invalid =
                List.of(
                        () -> candidate(
                                null, "provider-001", mismatch,
                                100, 101, "PAID", "PAID", action,
                                "a".repeat(64), status, created
                        ),
                        () -> candidate(
                                type, null, mismatch,
                                100, 101, "PAID", "PAID", action,
                                "a".repeat(64), status, created
                        ),
                        () -> candidate(
                                type, "provider-001", null,
                                100, 101, "PAID", "PAID", action,
                                "a".repeat(64), status, created
                        ),
                        () -> candidate(
                                type, "provider-001", mismatch,
                                -1, 101, "PAID", "PAID", action,
                                "a".repeat(64), status, created
                        ),
                        () -> candidate(
                                type, "provider-001", mismatch,
                                100, -1, "PAID", "PAID", action,
                                "a".repeat(64), status, created
                        ),
                        () -> candidate(
                                type, "provider-001", mismatch,
                                100, 101, "bad", "PAID", action,
                                "a".repeat(64), status, created
                        ),
                        () -> candidate(
                                type, "provider-001", mismatch,
                                100, 101, "PAID", "bad", action,
                                "a".repeat(64), status, created
                        ),
                        () -> candidate(
                                type, "provider-001", mismatch,
                                100, 101, "PAID", "PAID", null,
                                "a".repeat(64), status, created
                        ),
                        () -> candidate(
                                type, "provider-001", mismatch,
                                100, 101, "PAID", "PAID", action,
                                null, status, created
                        ),
                        () -> candidate(
                                type, "provider-001", mismatch,
                                100, 101, "PAID", "PAID", action,
                                "invalid-hash", status, created
                        ),
                        () -> candidate(
                                type, "provider-001", mismatch,
                                100, 101, "PAID", "PAID", action,
                                "a".repeat(64), null, created
                        ),
                        () -> candidate(
                                type, "provider-001", mismatch,
                                100, 101, "PAID", "PAID", action,
                                "a".repeat(64), status, null
                        )
                );

        invalid.forEach(value -> assertThatThrownBy(value)
                .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void requiresCompleteAndConsistentResolutionEvidence() {
        String actor = "40000000-0000-4000-8000-000000000001";
        Instant at = Instant.parse("2026-07-25T01:00:00Z");
        var noChange = MonetizationReconciliationCase.ResolutionAction
                .NO_FINANCIAL_CHANGE;
        var compensation =
                MonetizationReconciliationCase.ResolutionAction
                        .COMPENSATING_LEDGER_POSTED;
        List<org.assertj.core.api.ThrowableAssert.ThrowingCallable> invalid =
                List.of(
                        () -> resolved(null, "Valid resolution reason.",
                                noChange, null, at),
                        () -> resolved(actor, null, noChange, null, at),
                        () -> resolved(actor, " ", noChange, null, at),
                        () -> resolved(actor, "short", noChange, null, at),
                        () -> resolved(actor, "x".repeat(501),
                                noChange, null, at),
                        () -> resolved(actor, "Valid resolution reason.",
                                null, null, at),
                        () -> resolved(actor, "Valid resolution reason.",
                                noChange, null, null),
                        () -> resolved(actor, "Valid resolution reason.",
                                compensation, null, at),
                        () -> resolved(actor, "Valid resolution reason.",
                                noChange,
                                "50000000-0000-4000-8000-000000000001",
                                at)
                );

        invalid.forEach(value -> assertThatThrownBy(value)
                .isInstanceOf(IllegalArgumentException.class));
    }

    private static MonetizationReconciliationCase value(
            String reference,
            String providerStatus
    ) {
        return new MonetizationReconciliationCase(
                "10000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                MonetizationReconciliationCase.SubjectType.TOPUP,
                "30000000-0000-4000-8000-000000000001",
                reference,
                MonetizationReconciliationCase.Mismatch.AMOUNT_MISMATCH,
                100,
                101,
                "PAID",
                providerStatus,
                MonetizationReconciliationCase.RecommendedAction
                        .INVESTIGATE_PROVIDER_EVENT,
                "a".repeat(64),
                MonetizationReconciliationCase.Status.OPEN,
                Instant.parse("2026-07-25T00:00:00Z"),
                null,
                null,
                null,
                null,
                null
        );
    }

    private static MonetizationReconciliationCase candidate(
            MonetizationReconciliationCase.SubjectType type,
            String reference,
            MonetizationReconciliationCase.Mismatch mismatch,
            long localAmount,
            long providerAmount,
            String localStatus,
            String providerStatus,
            MonetizationReconciliationCase.RecommendedAction recommendation,
            String evidenceHash,
            MonetizationReconciliationCase.Status status,
            Instant createdAt
    ) {
        return new MonetizationReconciliationCase(
                "10000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                type,
                "30000000-0000-4000-8000-000000000001",
                reference,
                mismatch,
                localAmount,
                providerAmount,
                localStatus,
                providerStatus,
                recommendation,
                evidenceHash,
                status,
                createdAt,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static MonetizationReconciliationCase resolved(
            String actor,
            String reason,
            MonetizationReconciliationCase.ResolutionAction action,
            String compensation,
            Instant at
    ) {
        return new MonetizationReconciliationCase(
                "10000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                MonetizationReconciliationCase.SubjectType.TOPUP,
                "30000000-0000-4000-8000-000000000001",
                "provider-001",
                MonetizationReconciliationCase.Mismatch.AMOUNT_MISMATCH,
                100,
                101,
                "PAID",
                "PAID",
                MonetizationReconciliationCase.RecommendedAction
                        .INVESTIGATE_PROVIDER_EVENT,
                "a".repeat(64),
                MonetizationReconciliationCase.Status.RESOLVED,
                Instant.parse("2026-07-25T00:00:00Z"),
                actor,
                reason,
                action,
                compensation,
                at
        );
    }
}
