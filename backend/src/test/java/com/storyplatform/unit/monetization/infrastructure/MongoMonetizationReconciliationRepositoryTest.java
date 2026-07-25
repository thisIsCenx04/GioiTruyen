package com.storyplatform.unit.monetization.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.monetization.application
        .MonetizationReconciliationOperations;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationRepository;
import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoLedgerTransactionDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoMonetizationReconciliationCaseDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoMonetizationReconciliationRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoMonetizationReconciliationRunDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoPaymentEventDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupRequestDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalPayoutDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoMonetizationReconciliationRepositoryTest {

    private static final Instant FROM =
            Instant.parse("2026-07-24T00:00:00Z");
    private static final Instant TO =
            Instant.parse("2026-07-25T00:00:00Z");

    @Test
    void persistsRunCaseResolutionAndCompensationEvidence() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository =
                new MongoMonetizationReconciliationRepository(mongo);
        var runDocument = runDocument();
        var caseDocument = caseDocument();
        when(mongo.findOne(
                any(Query.class),
                eq(MongoMonetizationReconciliationRunDocument.class)
        )).thenReturn(runDocument);
        when(mongo.findById(
                caseDocument.id(),
                MongoMonetizationReconciliationCaseDocument.class
        )).thenReturn(caseDocument);
        when(mongo.exists(
                any(Query.class),
                eq(MongoLedgerTransactionDocument.class)
        )).thenReturn(true);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoMonetizationReconciliationRunDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoMonetizationReconciliationCaseDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertThat(repository.findRun("bank-provider", FROM, TO))
                .contains(run());
        repository.insertRun(run());
        repository.insertCase(openCase());
        assertThat(repository.findCase(caseDocument.id()))
                .contains(openCase());
        assertThat(repository.hasPostedLedgerTransaction(
                "40000000-0000-4000-8000-000000000001"
        )).isTrue();
        var resolved = openCase().resolve(
                "50000000-0000-4000-8000-000000000001",
                "Verified with posted compensation.",
                MonetizationReconciliationCase.ResolutionAction
                        .COMPENSATING_LEDGER_POSTED,
                "40000000-0000-4000-8000-000000000001",
                TO
        );
        assertThat(repository.resolveCase(openCase(), resolved)).isTrue();
        repository.complete(
                run().id(),
                new MonetizationReconciliationOperations.Summary(
                        run().id(), 1, 1, false
                ),
                TO
        );

        verify(mongo).insert(any(
                MongoMonetizationReconciliationRunDocument.class
        ));
        verify(mongo).insert(any(
                MongoMonetizationReconciliationCaseDocument.class
        ));
    }

    @Test
    void emptyEvidenceWindowReturnsImmutableEmptyList() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(
                any(Query.class),
                eq(com.storyplatform.monetization.infrastructure.persistence
                        .MongoPaymentEventDocument.class)
        )).thenReturn(List.of());
        when(mongo.find(
                any(Query.class),
                eq(com.storyplatform.monetization.infrastructure.persistence
                        .MongoWithdrawalPayoutDocument.class)
        )).thenReturn(List.of());

        assertThat(new MongoMonetizationReconciliationRepository(mongo)
                .findLocalEntries("bank-provider", FROM, TO)).isEmpty();
    }

    @Test
    void joinsProviderEventsToTopupWithdrawalAndLedgerEvidence() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(
                any(Query.class),
                eq(MongoPaymentEventDocument.class)
        )).thenReturn(List.of(payment()));
        when(mongo.findOne(
                any(Query.class),
                eq(MongoTopupRequestDocument.class)
        )).thenReturn(topup());
        when(mongo.findOne(
                any(Query.class),
                eq(MongoLedgerTransactionDocument.class)
        )).thenReturn(ledger());
        when(mongo.find(
                any(Query.class),
                eq(MongoWithdrawalPayoutDocument.class)
        )).thenReturn(List.of(payout()));
        when(mongo.findById(
                payout().withdrawalId(),
                MongoWithdrawalDocument.class
        )).thenReturn(withdrawal());

        var evidence =
                new MongoMonetizationReconciliationRepository(mongo)
                        .findLocalEntries(
                                "bank-provider", FROM, TO
                        );

        assertThat(evidence).hasSize(2);
        assertThat(evidence)
                .extracting(
                        MonetizationReconciliationRepository.LocalEntry
                                ::subjectType
                )
                .containsExactly(
                        MonetizationReconciliationCase.SubjectType.TOPUP,
                        MonetizationReconciliationCase.SubjectType.WITHDRAWAL
                );
        assertThat(evidence)
                .extracting(
                        MonetizationReconciliationRepository.LocalEntry
                                ::ledgerTransactionId
                )
                .doesNotContainNull();
    }

    private static MonetizationReconciliationRepository.Run run() {
        return new MonetizationReconciliationRepository.Run(
                "10000000-0000-4000-8000-000000000001",
                "bank-provider",
                FROM,
                TO,
                "a".repeat(64),
                MonetizationReconciliationRepository.State.RUNNING,
                null,
                FROM,
                null
        );
    }

    private static MongoMonetizationReconciliationRunDocument
            runDocument() {
        return new MongoMonetizationReconciliationRunDocument(
                run().id(),
                run().provider(),
                run().from(),
                run().to(),
                run().statementHash(),
                run().state().name(),
                null,
                run().startedAt(),
                null
        );
    }

    private static MonetizationReconciliationCase openCase() {
        return new MonetizationReconciliationCase(
                "20000000-0000-4000-8000-000000000001",
                run().id(),
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
                "b".repeat(64),
                MonetizationReconciliationCase.Status.OPEN,
                FROM,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static MongoMonetizationReconciliationCaseDocument
            caseDocument() {
        var value = openCase();
        return new MongoMonetizationReconciliationCaseDocument(
                value.id(),
                value.runId(),
                value.subjectType().name(),
                value.subjectId(),
                value.providerReference(),
                value.mismatch().name(),
                value.localAmount(),
                value.providerAmount(),
                value.localStatus(),
                value.providerStatus(),
                value.recommendedAction().name(),
                value.evidenceHash(),
                value.status().name(),
                value.createdAt(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private static MongoPaymentEventDocument payment() {
        return new MongoPaymentEventDocument(
                "60000000-0000-4000-8000-000000000001",
                "bank-provider",
                "event-001",
                "bank-001",
                100_000,
                "GT1234567890AB",
                FROM.plusSeconds(60),
                FROM.plusSeconds(120),
                "c".repeat(64),
                "{\"event\":\"payment\"}",
                "MATCHED"
        );
    }

    private static MongoTopupRequestDocument topup() {
        return new MongoTopupRequestDocument(
                "70000000-0000-4000-8000-000000000001",
                "user-001",
                100_000,
                90_000,
                new BigDecimal("10.00"),
                1,
                "GT1234567890AB",
                "qr-payload",
                "CREDITED",
                TO.plusSeconds(3_600),
                FROM,
                "d".repeat(64),
                "e".repeat(64)
        );
    }

    private static MongoLedgerTransactionDocument ledger() {
        return new MongoLedgerTransactionDocument(
                "80000000-0000-4000-8000-000000000001",
                "TOPUP",
                "topup_request",
                topup().id(),
                "POSTED",
                List.of(),
                "f".repeat(64),
                null,
                FROM.plusSeconds(180)
        );
    }

    private static MongoWithdrawalPayoutDocument payout() {
        return new MongoWithdrawalPayoutDocument(
                "90000000-0000-4000-8000-000000000001",
                "bank-provider",
                "withdrawal:90000000-0000-4000-8000-000000000001",
                "PAID",
                1,
                "payout-001",
                null,
                null,
                null,
                FROM.plusSeconds(60),
                FROM.plusSeconds(180),
                "a0000000-0000-4000-8000-000000000001",
                null
        );
    }

    private static MongoWithdrawalDocument withdrawal() {
        return new MongoWithdrawalDocument(
                payout().withdrawalId(),
                "a1000000-0000-4000-8000-000000000001",
                "a2000000-0000-4000-8000-000000000001",
                100_000,
                20_000,
                80_000,
                "withdrawal-fee-2026.1",
                new MongoWithdrawalDocument.DestinationSnapshot(
                        "a3000000-0000-4000-8000-000000000001",
                        "VCB **** 1234",
                        "encrypted:v1:provider-token",
                        1,
                        FROM.minusSeconds(60)
                ),
                "PAID",
                "a4000000-0000-4000-8000-000000000001",
                "a5000000-0000-4000-8000-000000000001",
                "1".repeat(64),
                "2".repeat(64),
                FROM.minusSeconds(120),
                "a6000000-0000-4000-8000-000000000001",
                "Verified finance approval.",
                "STANDARD",
                "withdrawal-risk-2026.1",
                "3".repeat(64),
                "4".repeat(64),
                null,
                FROM.minusSeconds(60)
        );
    }
}
