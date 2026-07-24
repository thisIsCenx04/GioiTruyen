package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port.ManualTopupAuthorizer;
import com.storyplatform.monetization.application.port.ManualTopupRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.WalletAccount;
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

public final class ManualTopupService implements ManualTopupOperations {

    private static final String CLEARING_OWNER = "topup-clearing";
    private final ManualTopupRepository repository;
    private final ManualTopupAuthorizer authorizer;
    private final WalletOperations wallets;
    private final LedgerOperations ledger;
    private final OutboxAppender outbox;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public ManualTopupService(
            ManualTopupRepository repository,
            ManualTopupAuthorizer authorizer,
            WalletOperations wallets,
            LedgerOperations ledger,
            OutboxAppender outbox,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.authorizer = Objects.requireNonNull(authorizer);
        this.wallets = Objects.requireNonNull(wallets);
        this.ledger = Objects.requireNonNull(ledger);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public Approval approve(
            String actorId,
            String topupId,
            String reauthenticationToken,
            String reason,
            String evidenceReference
    ) {
        String normalizedReason = bounded(
                reason, 10, 500, "Approval reason"
        );
        String evidence = bounded(
                evidenceReference, 3, 512, "Evidence reference"
        );
        if (!evidence.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{2,511}")) {
            throw failure(
                    "Evidence reference has an invalid format.",
                    ManualTopupException.Kind.INVALID
            );
        }
        var topup = repository.findPendingTopup(topupId)
                .orElseThrow(() -> failure(
                        "Pending top-up request was not found.",
                        ManualTopupException.Kind.NOT_FOUND
                ));
        var event = repository.findPendingEvent(topupId)
                .orElseThrow(() -> failure(
                        "Pending payment evidence was not found.",
                        ManualTopupException.Kind.NOT_FOUND
                ));
        if (!authorizer.consume(
                actorId,
                reauthenticationToken,
                topupId
        )) {
            throw failure(
                    "A scoped reauthentication grant is required.",
                    ManualTopupException.Kind.FORBIDDEN
            );
        }
        if (topup.creditedXu() <= 0) {
            throw failure(
                    "Top-up has no positive XU amount to credit.",
                    ManualTopupException.Kind.INVALID
            );
        }
        wallets.open(WalletAccount.OwnerType.PLATFORM, CLEARING_OWNER);
        wallets.open(WalletAccount.OwnerType.USER, topup.userId());
        var posting = ledger.post(new LedgerOperations.Command(
                LedgerTransaction.Type.TOPUP,
                "topup_request",
                topup.id(),
                List.of(
                        entry(
                                WalletAccount.OwnerType.PLATFORM,
                                CLEARING_OWNER,
                                LedgerEntry.Side.DEBIT,
                                topup.creditedXu()
                        ),
                        entry(
                                WalletAccount.OwnerType.USER,
                                topup.userId(),
                                LedgerEntry.Side.CREDIT,
                                topup.creditedXu()
                        )
                ),
                hash("topup:" + topup.id()),
                event.id(),
                actorId,
                null
        ));
        Instant now = clock.instant();
        if (!repository.complete(
                topup.id(),
                event.id(),
                posting.transaction().id(),
                actorId,
                normalizedReason,
                evidence,
                now
        )) {
            throw failure(
                    "Top-up already has a concurrent final decision.",
                    ManualTopupException.Kind.CONFLICT
            );
        }
        outbox.append(new IntegrationEvent(
                ids.get(),
                "monetization.topup.manuallyapproved",
                1,
                now,
                event.id(),
                "topup_request",
                topup.id(),
                actorId,
                null,
                new ManualApproval(
                        event.id(),
                        posting.transaction().id()
                )
        ));
        return new Approval(
                topup.id(),
                event.id(),
                posting.transaction().id(),
                "CREDITED",
                now
        );
    }

    private static LedgerEntry entry(
            WalletAccount.OwnerType ownerType,
            String ownerId,
            LedgerEntry.Side side,
            long amount
    ) {
        return new LedgerEntry(
                WalletOperations.accountId(ownerType, ownerId),
                side,
                amount
        );
    }

    private static String bounded(
            String value,
            int minimum,
            int maximum,
            String field
    ) {
        if (value == null
                || value.strip().length() < minimum
                || value.strip().length() > maximum) {
            throw failure(
                    field + " has an invalid length.",
                    ManualTopupException.Kind.INVALID
            );
        }
        return value.strip();
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

    private static ManualTopupException failure(
            String message,
            ManualTopupException.Kind kind
    ) {
        return new ManualTopupException(message, kind);
    }

    public record ManualApproval(
            String paymentEventId,
            String ledgerTransactionId
    ) {
    }
}
