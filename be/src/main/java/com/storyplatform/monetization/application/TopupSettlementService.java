package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port.TopupSettlementRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.domain.TopupRequest;
import com.storyplatform.monetization.domain.WalletAccount;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class TopupSettlementService
        implements TopupSettlementOperations {

    private static final String CLEARING_OWNER = "topup-clearing";
    private final TopupSettlementRepository repository;
    private final WalletOperations wallets;
    private final LedgerOperations ledger;
    private final Clock clock;

    public TopupSettlementService(
            TopupSettlementRepository repository,
            WalletOperations wallets,
            LedgerOperations ledger,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.wallets = Objects.requireNonNull(wallets);
        this.ledger = Objects.requireNonNull(ledger);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Result settle(String provider, String providerEventId) {
        PaymentEvent event = repository.findEvent(provider, providerEventId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment event was not found."
                ));
        if (event.status() != PaymentEvent.Status.RECEIVED) {
            return Result.REPLAYED;
        }
        TopupRequest topup = repository.findTopup(
                event.transferReference()
        ).orElse(null);
        if (topup == null) {
            return review(
                    repository.flagForReview(
                    event.id(), null, "TOPUP_NOT_FOUND", clock.instant()
                    )
            );
        }
        if (topup.status() != TopupRequest.Status.AWAITING_PAYMENT
                || topup.creditedXu() <= 0
                || !clock.instant().isBefore(topup.expiresAt())) {
            return review(repository.flagForReview(
                    event.id(),
                    topup.id(),
                    "STATE_EXPIRY_OR_CREDIT_MISMATCH",
                    clock.instant()
            ));
        }
        if (event.amountVnd() != topup.amountVnd()) {
            return review(repository.flagForReview(
                    event.id(),
                    topup.id(),
                    "AMOUNT_MISMATCH",
                    clock.instant()
            ));
        }
        wallets.open(WalletAccount.OwnerType.PLATFORM, CLEARING_OWNER);
        wallets.open(WalletAccount.OwnerType.USER, topup.userId());
        var posting = ledger.post(new LedgerOperations.Command(
                LedgerTransaction.Type.TOPUP,
                "topup_request",
                topup.id(),
                List.of(
                        new LedgerEntry(
                                WalletOperations.accountId(
                                        WalletAccount.OwnerType.PLATFORM,
                                        CLEARING_OWNER
                                ),
                                LedgerEntry.Side.DEBIT,
                                topup.creditedXu()
                        ),
                        new LedgerEntry(
                                WalletOperations.accountId(
                                        WalletAccount.OwnerType.USER,
                                        topup.userId()
                                ),
                                LedgerEntry.Side.CREDIT,
                                topup.creditedXu()
                        )
                ),
                hash("topup:" + topup.id()),
                event.id(),
                topup.userId(),
                null
        ));
        if (!repository.complete(
                event.id(),
                topup.id(),
                posting.transaction().id(),
                clock.instant()
        )) {
            throw new LedgerConflictException(
                    "Top-up settlement lost a concurrent decision."
            );
        }
        return Result.CREDITED;
    }

    private static Result review(boolean changed) {
        return changed ? Result.PENDING_REVIEW : Result.REPLAYED;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
