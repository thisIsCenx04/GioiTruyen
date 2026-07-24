package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .WithdrawalCursorCodec;
import com.storyplatform.monetization.application.port
        .WithdrawalDestinationDirectory;
import com.storyplatform.monetization.application.port.WithdrawalRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.WalletAccount;
import com.storyplatform.monetization.domain.Withdrawal;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;

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

public final class WithdrawalService implements WithdrawalOperations {

    public static final String REQUEST_WITHDRAWAL = "finance:request";
    private static final String ROUTE = "/teams/{teamId}/withdrawals";
    private final WithdrawalRepository repository;
    private final WithdrawalDestinationDirectory destinations;
    private final WithdrawalCursorCodec cursors;
    private final TeamPermissionAuthorizer permissions;
    private final WalletOperations wallets;
    private final LedgerOperations ledger;
    private final OutboxAppender outbox;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public WithdrawalService(
            WithdrawalRepository repository,
            WithdrawalDestinationDirectory destinations,
            WithdrawalCursorCodec cursors,
            TeamPermissionAuthorizer permissions,
            WalletOperations wallets,
            LedgerOperations ledger,
            OutboxAppender outbox,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.destinations = Objects.requireNonNull(destinations);
        this.cursors = Objects.requireNonNull(cursors);
        this.permissions = Objects.requireNonNull(permissions);
        this.wallets = Objects.requireNonNull(wallets);
        this.ledger = Objects.requireNonNull(ledger);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public Receipt create(
            String actorId,
            String teamId,
            String idempotencyKey,
            long grossAmountXu,
            String destinationId
    ) {
        actorId = uuid(actorId, "Withdrawal requester is invalid.");
        teamId = uuid(teamId, "Withdrawal team is invalid.");
        destinationId = uuid(
                destinationId,
                "Withdrawal destination is invalid."
        );
        authorize(actorId, teamId);
        requireAmount(grossAmountXu);
        String keyHash = hash(
                actorId + "\n" + ROUTE + "\n" + teamId + "\n"
                        + requireKey(idempotencyKey)
        );
        String requestHash = hash(
                teamId + "\n" + grossAmountXu + "\n" + destinationId
        );
        var replay = repository.findByIdempotencyKeyHash(keyHash);
        if (replay.isPresent()) {
            Withdrawal existing = replay.orElseThrow();
            if (!MessageDigest.isEqual(
                    existing.requestHash().getBytes(
                            StandardCharsets.US_ASCII
                    ),
                    requestHash.getBytes(StandardCharsets.US_ASCII)
            )) {
                throw failure(
                        "Idempotency-Key was reused.",
                        WithdrawalException.Kind.CONFLICT
                );
            }
            return receipt(existing, true);
        }
        Instant now = clock.instant();
        Withdrawal.DestinationSnapshot destination = destinations
                .findEligible(teamId, destinationId, now)
                .orElseThrow(() -> failure(
                        "Withdrawal destination is unavailable.",
                        WithdrawalException.Kind.DESTINATION_UNAVAILABLE
                ));
        String withdrawalId = ids.get().toString();
        wallets.open(WalletAccount.OwnerType.TEAM, teamId);
        LedgerOperations.Posting posting;
        try {
            posting = ledger.post(new LedgerOperations.Command(
                    LedgerTransaction.Type.WITHDRAWAL_RESERVE,
                    "withdrawal",
                    withdrawalId,
                    reservationEntries(teamId, grossAmountXu),
                    keyHash,
                    withdrawalId,
                    actorId,
                    teamId
            ));
        } catch (InsufficientWalletBalanceException exception) {
            throw failure(
                    exception.getMessage(),
                    WithdrawalException.Kind.INSUFFICIENT_BALANCE
            );
        } catch (LedgerConflictException exception) {
            throw failure(
                    exception.getMessage(),
                    WithdrawalException.Kind.CONFLICT
            );
        }
        Withdrawal stored = repository.insert(new Withdrawal(
                withdrawalId,
                teamId,
                WalletOperations.accountId(
                        WalletAccount.OwnerType.TEAM,
                        teamId
                ),
                grossAmountXu,
                destination,
                Withdrawal.State.PENDING_REVIEW,
                actorId,
                posting.transaction().id(),
                keyHash,
                requestHash,
                now
        ));
        outbox.append(new IntegrationEvent(
                ids.get(),
                "monetization.withdrawal.requested",
                1,
                now,
                withdrawalId,
                "withdrawal",
                withdrawalId,
                actorId,
                teamId,
                new WithdrawalRequested(teamId, grossAmountXu)
        ));
        return receipt(stored, false);
    }

    @Override
    public Page list(
            String actorId,
            String teamId,
            String cursor,
            int limit
    ) {
        actorId = uuid(actorId, "Withdrawal requester is invalid.");
        teamId = uuid(teamId, "Withdrawal team is invalid.");
        authorize(actorId, teamId);
        if (limit < 1 || limit > 100) {
            throw failure(
                    "Withdrawal page limit must be 1 to 100.",
                    WithdrawalException.Kind.INVALID
            );
        }
        WithdrawalCursorCodec.Position after = null;
        if (cursor != null && !cursor.isBlank()) {
            after = cursors.decode(cursor).orElseThrow(() -> failure(
                    "Withdrawal cursor is invalid.",
                    WithdrawalException.Kind.INVALID
            ));
        }
        List<Withdrawal> values = repository.findByTeam(
                teamId, after, limit + 1
        );
        boolean hasMore = values.size() > limit;
        List<Withdrawal> page = values.stream().limit(limit).toList();
        String next = null;
        if (hasMore && !page.isEmpty()) {
            Withdrawal last = page.getLast();
            next = cursors.encode(new WithdrawalCursorCodec.Position(
                    last.createdAt(), last.id()
            ));
        }
        return new Page(
                page.stream().map(value -> receipt(value, false)).toList(),
                next
        );
    }

    private void authorize(String actorId, String teamId) {
        if (!permissions.allows(
                actorId,
                teamId,
                REQUEST_WITHDRAWAL
        )) {
            throw failure(
                    "Withdrawal permission is required.",
                    WithdrawalException.Kind.FORBIDDEN
            );
        }
    }

    private static List<LedgerEntry> reservationEntries(
            String teamId,
            long amountXu
    ) {
        String accountId = WalletOperations.accountId(
                WalletAccount.OwnerType.TEAM,
                teamId
        );
        return List.of(
                new LedgerEntry(
                        accountId,
                        LedgerEntry.Side.DEBIT,
                        LedgerEntry.Bucket.AVAILABLE,
                        amountXu
                ),
                new LedgerEntry(
                        accountId,
                        LedgerEntry.Side.CREDIT,
                        LedgerEntry.Bucket.RESERVED,
                        amountXu
                )
        );
    }

    private static void requireAmount(long amount) {
        if (amount < Withdrawal.MINIMUM_GROSS_XU
                || amount > Withdrawal.MAXIMUM_GROSS_XU) {
            throw failure(
                    "Withdrawal gross amount must be 100000 to "
                            + "1000000000 XU.",
                    WithdrawalException.Kind.INVALID
            );
        }
    }

    private static String requireKey(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9._:-]{16,128}")) {
            throw failure(
                    "A valid Idempotency-Key is required.",
                    WithdrawalException.Kind.INVALID
            );
        }
        return value;
    }

    private static String uuid(String value, String message) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw failure(message, WithdrawalException.Kind.INVALID);
        }
    }

    private static Receipt receipt(
            Withdrawal value,
            boolean replayed
    ) {
        return new Receipt(
                value.id(),
                value.teamId(),
                value.grossAmountXu(),
                value.destination().maskedLabel(),
                value.state().name(),
                replayed,
                value.createdAt()
        );
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

    public record WithdrawalRequested(String teamId, long grossAmountXu) {
    }
}
