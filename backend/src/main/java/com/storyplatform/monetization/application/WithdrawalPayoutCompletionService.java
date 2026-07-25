package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutRepository;
import com.storyplatform.monetization.application.port.WithdrawalRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.WalletAccount;
import com.storyplatform.monetization.domain.Withdrawal;
import com.storyplatform.monetization.domain.WithdrawalPayout;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class WithdrawalPayoutCompletionService
        implements WithdrawalPayoutCompletionOperations {

    private static final String PAYOUT_CLEARING =
            "withdrawal-payout-clearing";
    private static final String FEE_REVENUE =
            "withdrawal-fee-revenue";
    private final WithdrawalRepository withdrawals;
    private final WithdrawalPayoutRepository payouts;
    private final WalletOperations wallets;
    private final LedgerOperations ledger;
    private final OutboxAppender outbox;
    private final Duration retryDelay;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public WithdrawalPayoutCompletionService(
            WithdrawalRepository withdrawals,
            WithdrawalPayoutRepository payouts,
            WalletOperations wallets,
            LedgerOperations ledger,
            OutboxAppender outbox,
            Duration retryDelay,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.withdrawals = Objects.requireNonNull(withdrawals);
        this.payouts = Objects.requireNonNull(payouts);
        this.wallets = Objects.requireNonNull(wallets);
        this.ledger = Objects.requireNonNull(ledger);
        this.outbox = Objects.requireNonNull(outbox);
        if (retryDelay == null
                || retryDelay.isZero()
                || retryDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "Withdrawal payout retry delay is invalid."
            );
        }
        this.retryDelay = retryDelay;
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public void complete(
            WithdrawalPayoutClaimOperations.Claim claim,
            WithdrawalPayoutGateway.Result result
    ) {
        Objects.requireNonNull(claim);
        Objects.requireNonNull(result);
        switch (result.status()) {
            case PENDING -> reschedule(
                    claim,
                    result.providerReference(),
                    null
            );
            case PAID -> paid(claim, result.providerReference());
            case FAILED -> failed(
                    claim,
                    result.providerReference(),
                    result.errorCode()
            );
        }
    }

    @Override
    public void retry(
            WithdrawalPayoutClaimOperations.Claim claim,
            String errorCode
    ) {
        reschedule(claim, claim.payout().providerReference(), errorCode);
    }

    private void reschedule(
            WithdrawalPayoutClaimOperations.Claim claim,
            String reference,
            String errorCode
    ) {
        WithdrawalPayout replacement = claim.payout().retry(
                reference,
                errorCode,
                clock.instant().plus(backoff(claim.payout().attempt()))
        );
        if (!payouts.update(claim.payout(), replacement)) {
            throw conflict();
        }
    }

    private void paid(
            WithdrawalPayoutClaimOperations.Claim claim,
            String providerReference
    ) {
        Withdrawal withdrawal = claim.withdrawal();
        wallets.open(
                WalletAccount.OwnerType.PLATFORM_LIABILITY,
                PAYOUT_CLEARING
        );
        if (withdrawal.feeXu() > 0) {
            wallets.open(
                    WalletAccount.OwnerType.PLATFORM_LIABILITY,
                    FEE_REVENUE
            );
        }
        var posting = ledger.post(new LedgerOperations.Command(
                LedgerTransaction.Type.WITHDRAWAL_SETTLEMENT,
                "withdrawal_settlement",
                withdrawal.id(),
                settlementEntries(withdrawal),
                hash("withdrawal-settlement\n" + withdrawal.id()),
                withdrawal.id(),
                "system:withdrawal-payout",
                withdrawal.teamId()
        ));
        Instant now = clock.instant();
        WithdrawalPayout replacement = claim.payout().paid(
                providerReference,
                posting.transaction().id(),
                now
        );
        if (!payouts.update(claim.payout(), replacement)
                || !withdrawals.transition(
                withdrawal.id(),
                Withdrawal.State.PROCESSING,
                Withdrawal.State.PAID,
                null
        )) {
            throw conflict();
        }
        event(withdrawal, replacement, now);
    }

    private void failed(
            WithdrawalPayoutClaimOperations.Claim claim,
            String providerReference,
            String errorCode
    ) {
        Withdrawal withdrawal = claim.withdrawal();
        var release = ledger.post(new LedgerOperations.Command(
                LedgerTransaction.Type.WITHDRAWAL_RELEASE,
                "withdrawal_provider_failure",
                withdrawal.id(),
                List.of(
                        new LedgerEntry(
                                withdrawal.accountId(),
                                LedgerEntry.Side.DEBIT,
                                LedgerEntry.Bucket.RESERVED,
                                withdrawal.grossAmountXu()
                        ),
                        new LedgerEntry(
                                withdrawal.accountId(),
                                LedgerEntry.Side.CREDIT,
                                LedgerEntry.Bucket.AVAILABLE,
                                withdrawal.grossAmountXu()
                        )
                ),
                hash("withdrawal-provider-failure\n" + withdrawal.id()),
                withdrawal.id(),
                "system:withdrawal-payout",
                withdrawal.teamId()
        ));
        Instant now = clock.instant();
        WithdrawalPayout replacement = claim.payout().failed(
                providerReference,
                errorCode,
                release.transaction().id(),
                now
        );
        if (!payouts.update(claim.payout(), replacement)
                || !withdrawals.transition(
                withdrawal.id(),
                Withdrawal.State.PROCESSING,
                Withdrawal.State.FAILED,
                release.transaction().id()
        )) {
            throw conflict();
        }
        event(withdrawal, replacement, now);
    }

    private List<LedgerEntry> settlementEntries(Withdrawal value) {
        List<LedgerEntry> entries = new ArrayList<>(3);
        entries.add(new LedgerEntry(
                value.accountId(),
                LedgerEntry.Side.DEBIT,
                LedgerEntry.Bucket.RESERVED,
                value.grossAmountXu()
        ));
        entries.add(entry(PAYOUT_CLEARING, value.netAmountXu()));
        if (value.feeXu() > 0) {
            entries.add(entry(FEE_REVENUE, value.feeXu()));
        }
        return List.copyOf(entries);
    }

    private static LedgerEntry entry(String ownerId, long amount) {
        return new LedgerEntry(
                WalletOperations.accountId(
                        WalletAccount.OwnerType.PLATFORM_LIABILITY,
                        ownerId
                ),
                LedgerEntry.Side.CREDIT,
                amount
        );
    }

    private Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 10);
        try {
            return retryDelay.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return retryDelay.multipliedBy(1L << 10);
        }
    }

    private void event(
            Withdrawal withdrawal,
            WithdrawalPayout payout,
            Instant at
    ) {
        outbox.append(new IntegrationEvent(
                ids.get(),
                "monetization.withdrawal."
                        + payout.state().name().toLowerCase(),
                1,
                at,
                withdrawal.id(),
                "withdrawal",
                withdrawal.id(),
                "system:withdrawal-payout",
                withdrawal.teamId(),
                new PayoutCompleted(
                        payout.provider(),
                        payout.providerReference(),
                        payout.state().name(),
                        payout.lastErrorCode()
                )
        ));
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

    private static WithdrawalException conflict() {
        return new WithdrawalException(
                "Withdrawal payout changed concurrently.",
                WithdrawalException.Kind.CONFLICT
        );
    }

    public record PayoutCompleted(
            String provider,
            String providerReference,
            String state,
            String errorCode
    ) {
    }
}
