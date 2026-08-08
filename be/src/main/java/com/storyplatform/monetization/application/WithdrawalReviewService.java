package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .WithdrawalRepository;
import com.storyplatform.monetization.application.port
        .WithdrawalReviewAuthorizer;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.Withdrawal;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class WithdrawalReviewService
        implements WithdrawalReviewOperations {

    private static final String RISK_RULE_VERSION =
            "withdrawal-risk-2026.1";
    private final WithdrawalRepository repository;
    private final WithdrawalReviewAuthorizer authorizer;
    private final LedgerOperations ledger;
    private final OutboxAppender outbox;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public WithdrawalReviewService(
            WithdrawalRepository repository,
            WithdrawalReviewAuthorizer authorizer,
            LedgerOperations ledger,
            OutboxAppender outbox,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.authorizer = Objects.requireNonNull(authorizer);
        this.ledger = Objects.requireNonNull(ledger);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public Decision approve(
            String actorId,
            String withdrawalId,
            String reauthenticationToken,
            String idempotencyKey,
            String reason
    ) {
        return review(
                Action.APPROVE,
                actorId,
                withdrawalId,
                reauthenticationToken,
                idempotencyKey,
                reason
        );
    }

    @Override
    public Decision reject(
            String actorId,
            String withdrawalId,
            String reauthenticationToken,
            String idempotencyKey,
            String reason
    ) {
        return review(
                Action.REJECT,
                actorId,
                withdrawalId,
                reauthenticationToken,
                idempotencyKey,
                reason
        );
    }

    private Decision review(
            Action action,
            String actorId,
            String withdrawalId,
            String reauthenticationToken,
            String idempotencyKey,
            String reason
    ) {
        actorId = uuid(actorId);
        withdrawalId = uuid(withdrawalId);
        String normalizedReason = reason(reason);
        Withdrawal withdrawal = repository.findById(withdrawalId)
                .orElseThrow(() -> failure(
                        "Withdrawal was not found.",
                        WithdrawalException.Kind.NOT_FOUND
                ));
        if (withdrawal.requestedBy().equals(actorId)) {
            throw failure(
                    "A requester cannot review their own withdrawal.",
                    WithdrawalException.Kind.FORBIDDEN
            );
        }
        String keyHash = hash(
                actorId + "\n/admin/withdrawals/{id}/"
                        + action.route + "\n" + withdrawalId + "\n"
                        + key(idempotencyKey)
        );
        String requestHash = hash(
                withdrawalId + "\n" + action + "\n" + normalizedReason
        );
        var replay = repository.findByReviewKeyHash(keyHash);
        if (replay.isPresent()) {
            Withdrawal existing = replay.orElseThrow();
            if (!existing.id().equals(withdrawalId)
                    || !MessageDigest.isEqual(
                    existing.reviewRequestHash().getBytes(
                            StandardCharsets.US_ASCII
                    ),
                    requestHash.getBytes(StandardCharsets.US_ASCII)
            )) {
                throw failure(
                        "Idempotency-Key was reused.",
                        WithdrawalException.Kind.CONFLICT
                );
            }
            return decision(existing, true);
        }
        if (withdrawal.state() != Withdrawal.State.PENDING_REVIEW) {
            throw failure(
                    "Withdrawal already has a final review decision.",
                    WithdrawalException.Kind.CONFLICT
            );
        }
        if (!authorizer.consume(
                actorId,
                reauthenticationToken,
                withdrawalId
        )) {
            throw failure(
                    "A scoped reauthentication grant is required.",
                    WithdrawalException.Kind.FORBIDDEN
            );
        }
        Instant now = clock.instant();
        Withdrawal reviewed = action == Action.APPROVE
                ? withdrawal.approved(
                actorId,
                normalizedReason,
                riskLevel(withdrawal),
                RISK_RULE_VERSION,
                keyHash,
                requestHash,
                now
        )
                : withdrawal.rejected(
                actorId,
                normalizedReason,
                riskLevel(withdrawal),
                RISK_RULE_VERSION,
                keyHash,
                requestHash,
                release(withdrawal, actorId).transaction().id(),
                now
        );
        if (!repository.decide(reviewed)) {
            throw failure(
                    "Withdrawal review changed concurrently.",
                    WithdrawalException.Kind.CONFLICT
            );
        }
        outbox.append(new IntegrationEvent(
                ids.get(),
                "monetization.withdrawal." + action.event,
                1,
                now,
                withdrawalId,
                "withdrawal",
                withdrawalId,
                actorId,
                withdrawal.teamId(),
                new WithdrawalReviewed(
                        action.name(),
                        normalizedReason,
                        reviewed.reviewRiskLevel(),
                        reviewed.reviewRiskRuleVersion(),
                        reviewed.releaseTransactionId()
                )
        ));
        return decision(reviewed, false);
    }

    private LedgerOperations.Posting release(
            Withdrawal value,
            String actorId
    ) {
        return ledger.post(new LedgerOperations.Command(
                LedgerTransaction.Type.WITHDRAWAL_RELEASE,
                "withdrawal_release",
                value.id(),
                List.of(
                        new LedgerEntry(
                                value.accountId(),
                                LedgerEntry.Side.DEBIT,
                                LedgerEntry.Bucket.RESERVED,
                                value.grossAmountXu()
                        ),
                        new LedgerEntry(
                                value.accountId(),
                                LedgerEntry.Side.CREDIT,
                                LedgerEntry.Bucket.AVAILABLE,
                                value.grossAmountXu()
                        )
                ),
                hash("withdrawal-release\n" + value.id()),
                value.id(),
                actorId,
                value.teamId()
        ));
    }

    private static Decision decision(
            Withdrawal value,
            boolean replayed
    ) {
        return new Decision(
                value.id(),
                value.state().name(),
                value.reviewedBy(),
                value.reviewReason(),
                value.reviewRiskLevel(),
                value.reviewRiskRuleVersion(),
                value.releaseTransactionId(),
                replayed,
                value.reviewedAt()
        );
    }

    private static String reason(String value) {
        if (value == null
                || value.strip().length() < 10
                || value.strip().length() > 500
                || value.chars().anyMatch(Character::isISOControl)) {
            throw failure(
                    "Withdrawal review reason is invalid.",
                    WithdrawalException.Kind.INVALID
            );
        }
        return value.strip();
    }

    private static String riskLevel(Withdrawal value) {
        return value.grossAmountXu() >= 1_000_000
                ? "HIGH_VALUE"
                : "STANDARD";
    }

    private static String key(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9._:-]{16,128}")) {
            throw failure(
                    "A valid Idempotency-Key is required.",
                    WithdrawalException.Kind.INVALID
            );
        }
        return value;
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw failure(
                    "Withdrawal identity is invalid.",
                    WithdrawalException.Kind.INVALID
            );
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static WithdrawalException failure(
            String message,
            WithdrawalException.Kind kind
    ) {
        return new WithdrawalException(message, kind);
    }

    private enum Action {
        APPROVE("approve", "approved"),
        REJECT("reject", "rejected");

        private final String route;
        private final String event;

        Action(String route, String event) {
            this.route = route;
            this.event = event;
        }
    }

    public record WithdrawalReviewed(
            String decision,
            String reason,
            String riskLevel,
            String riskRuleVersion,
            String releaseTransactionId
    ) {
    }
}
