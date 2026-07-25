package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application
        .MonetizationReconciliationException;
import com.storyplatform.monetization.application
        .MonetizationReconciliationOperations;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationGateway;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationRepository;
import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;
import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.domain.WithdrawalPayout;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MongoMonetizationReconciliationRepository
        implements MonetizationReconciliationRepository {

    private final MongoTemplate mongo;

    public MongoMonetizationReconciliationRepository(
            MongoTemplate mongo
    ) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<Run> findRun(
            String provider,
            Instant from,
            Instant to
    ) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("provider").is(provider)
                        .and("from").is(from)
                        .and("to").is(to)),
                MongoMonetizationReconciliationRunDocument.class
        )).map(MongoMonetizationReconciliationRunDocument::toDomain);
    }

    @Override
    public void insertRun(Run run) {
        try {
            mongo.insert(
                    MongoMonetizationReconciliationRunDocument.from(run)
            );
        } catch (DuplicateKeyException exception) {
            throw conflict();
        }
    }

    @Override
    public List<LocalEntry> findLocalEntries(
            String provider,
            Instant from,
            Instant to
    ) {
        List<LocalEntry> result = new ArrayList<>();
        result.addAll(topups(provider, from, to));
        result.addAll(withdrawals(provider, from, to));
        return List.copyOf(result);
    }

    @Override
    public void insertCase(MonetizationReconciliationCase mismatch) {
        try {
            mongo.insert(
                    MongoMonetizationReconciliationCaseDocument.from(
                            mismatch
                    )
            );
        } catch (DuplicateKeyException exception) {
            throw conflict();
        }
    }

    @Override
    public Optional<MonetizationReconciliationCase> findCase(
            String caseId
    ) {
        return Optional.ofNullable(mongo.findById(
                caseId,
                MongoMonetizationReconciliationCaseDocument.class
        )).map(MongoMonetizationReconciliationCaseDocument::toDomain);
    }

    @Override
    public boolean hasPostedLedgerTransaction(String transactionId) {
        return mongo.exists(
                Query.query(Criteria.where("_id").is(transactionId)
                        .and("state").is("POSTED")),
                MongoLedgerTransactionDocument.class
        );
    }

    @Override
    public boolean resolveCase(
            MonetizationReconciliationCase expected,
            MonetizationReconciliationCase resolved
    ) {
        return mongo.updateFirst(
                Query.query(Criteria.where("_id").is(expected.id())
                        .and("status").is("OPEN")
                        .and("evidenceHash").is(expected.evidenceHash())),
                new Update()
                        .set("status", resolved.status().name())
                        .set("resolvedBy", resolved.resolvedBy())
                        .set(
                                "resolutionReason",
                                resolved.resolutionReason()
                        )
                        .set(
                                "resolutionAction",
                                resolved.resolutionAction().name()
                        )
                        .set(
                                "compensationTransactionId",
                                resolved.compensationTransactionId()
                        )
                        .set("resolvedAt", resolved.resolvedAt()),
                MongoMonetizationReconciliationCaseDocument.class
        ).getModifiedCount() == 1;
    }

    @Override
    public void complete(
            String runId,
            MonetizationReconciliationOperations.Summary summary,
            Instant completedAt
    ) {
        var result = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(runId)
                        .and("state").is("RUNNING")),
                new Update()
                        .set("state", "COMPLETED")
                        .set(
                                "summary",
                                MongoMonetizationReconciliationRunDocument
                                        .SummaryDocument.from(summary)
                        )
                        .set("completedAt", completedAt),
                MongoMonetizationReconciliationRunDocument.class
        );
        if (result.getModifiedCount() != 1) {
            throw conflict();
        }
    }

    private List<LocalEntry> topups(
            String provider,
            Instant from,
            Instant to
    ) {
        List<MongoPaymentEventDocument> events = mongo.find(
                Query.query(Criteria.where("provider").is(provider)
                        .and("occurredAt").gte(from).lt(to)),
                MongoPaymentEventDocument.class
        );
        List<LocalEntry> result = new ArrayList<>(events.size());
        for (var document : events) {
            PaymentEvent event = document.toDomain();
            var topup = mongo.findOne(
                    Query.query(Criteria.where("transferReference")
                            .is(event.transferReference())),
                    MongoTopupRequestDocument.class
            );
            String subjectId = topup == null ? null : topup.id();
            String ledger = subjectId == null
                    ? null
                    : ledger("topup_request", subjectId);
            result.add(new LocalEntry(
                    MonetizationReconciliationCase.SubjectType.TOPUP,
                    subjectId,
                    event.bankReference(),
                    event.amountVnd(),
                    topupStatus(event.status()),
                    ledger,
                    event.occurredAt()
            ));
        }
        return result;
    }

    private List<LocalEntry> withdrawals(
            String provider,
            Instant from,
            Instant to
    ) {
        Criteria window = new Criteria().orOperator(
                Criteria.where("startedAt").gte(from).lt(to),
                Criteria.where("completedAt").gte(from).lt(to)
        );
        List<MongoWithdrawalPayoutDocument> payouts = mongo.find(
                Query.query(new Criteria().andOperator(
                        Criteria.where("provider").is(provider),
                        Criteria.where("providerReference").ne(null),
                        window
                )),
                MongoWithdrawalPayoutDocument.class
        );
        List<LocalEntry> result = new ArrayList<>(payouts.size());
        for (var document : payouts) {
            WithdrawalPayout payout = document.toDomain();
            var withdrawal = mongo.findById(
                    payout.withdrawalId(),
                    MongoWithdrawalDocument.class
            );
            if (withdrawal != null) {
                result.add(new LocalEntry(
                        MonetizationReconciliationCase.SubjectType.WITHDRAWAL,
                        payout.withdrawalId(),
                        payout.providerReference(),
                        withdrawal.netAmountXu(),
                        payoutStatus(payout.state()),
                        payout.state() == WithdrawalPayout.State.PAID
                                ? payout.settlementTransactionId()
                                : payout.releaseTransactionId(),
                        payout.completedAt() == null
                                ? payout.startedAt()
                                : payout.completedAt()
                ));
            }
        }
        return result;
    }

    private String ledger(String referenceType, String referenceId) {
        var value = mongo.findOne(
                Query.query(Criteria.where("referenceType")
                        .is(referenceType)
                        .and("referenceId").is(referenceId)
                        .and("state").is("POSTED")),
                MongoLedgerTransactionDocument.class
        );
        return value == null ? null : value.id();
    }

    private static MonetizationReconciliationGateway.Status topupStatus(
            PaymentEvent.Status status
    ) {
        return switch (status) {
            case MATCHED ->
                    MonetizationReconciliationGateway.Status.PAID;
            case REJECTED ->
                    MonetizationReconciliationGateway.Status.FAILED;
            case RECEIVED, PENDING_REVIEW ->
                    MonetizationReconciliationGateway.Status.PENDING;
        };
    }

    private static MonetizationReconciliationGateway.Status payoutStatus(
            WithdrawalPayout.State state
    ) {
        return switch (state) {
            case PAID -> MonetizationReconciliationGateway.Status.PAID;
            case FAILED -> MonetizationReconciliationGateway.Status.FAILED;
            case PROCESSING ->
                    MonetizationReconciliationGateway.Status.PENDING;
        };
    }

    private static MonetizationReconciliationException conflict() {
        return new MonetizationReconciliationException(
                "Reconciliation evidence changed concurrently."
        );
    }
}
