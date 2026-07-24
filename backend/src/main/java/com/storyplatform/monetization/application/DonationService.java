package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port.DonationRepository;
import com.storyplatform.monetization.domain.Donation;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.WalletAccount;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;

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

public final class DonationService implements DonationOperations {

    private static final String ROUTE = "/donations";
    private final DonationRepository repository;
    private final TeamStatusDirectory teams;
    private final WalletOperations wallets;
    private final LedgerOperations ledger;
    private final OutboxAppender outbox;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public DonationService(
            DonationRepository repository,
            TeamStatusDirectory teams,
            WalletOperations wallets,
            LedgerOperations ledger,
            OutboxAppender outbox,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.teams = Objects.requireNonNull(teams);
        this.wallets = Objects.requireNonNull(wallets);
        this.ledger = Objects.requireNonNull(ledger);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public Receipt donate(
            String donorId,
            String idempotencyKey,
            String teamId,
            long amountXu,
            String message
    ) {
        donorId = requireIdentity(donorId, "Donation donor is invalid.");
        teamId = requireIdentity(teamId, "Donation recipient is invalid.");
        String normalizedMessage = normalizeMessage(message);
        String keyHash = hash(
                donorId + "\n" + ROUTE + "\n"
                        + requireKey(idempotencyKey)
        );
        String requestHash = hash(
                teamId + "\n" + amountXu + "\n"
                        + Objects.toString(normalizedMessage, "")
        );
        var replay = repository.findByIdempotencyKeyHash(keyHash);
        if (replay.isPresent()) {
            if (!MessageDigest.isEqual(
                    replay.get().requestHash().getBytes(
                            StandardCharsets.US_ASCII
                    ),
                    requestHash.getBytes(StandardCharsets.US_ASCII)
            )) {
                throw failure(
                        "Idempotency-Key was reused for another donation.",
                        DonationException.Kind.CONFLICT
                );
            }
            return receipt(replay.orElseThrow(), true);
        }
        if (amountXu < 1
                || amountXu > Donation.MAXIMUM_AMOUNT_XU) {
            throw failure(
                    "Donation amount must be 1 to 1000000000 XU.",
                    DonationException.Kind.INVALID
            );
        }
        if (!teams.isActive(teamId)) {
            throw failure(
                    "Donation recipient team was not found.",
                    DonationException.Kind.TEAM_NOT_FOUND
            );
        }
        String donationId = ids.get().toString();
        String donorAccountId = WalletOperations.accountId(
                WalletAccount.OwnerType.USER,
                donorId
        );
        String teamAccountId = WalletOperations.accountId(
                WalletAccount.OwnerType.TEAM,
                teamId
        );
        wallets.open(WalletAccount.OwnerType.USER, donorId);
        wallets.open(WalletAccount.OwnerType.TEAM, teamId);
        LedgerOperations.Posting posting;
        try {
            posting = ledger.post(new LedgerOperations.Command(
                    LedgerTransaction.Type.DONATION,
                    "donation",
                    donationId,
                    List.of(
                            entry(
                                    WalletAccount.OwnerType.USER,
                                    donorId,
                                    LedgerEntry.Side.DEBIT,
                                    amountXu
                            ),
                            entry(
                                    WalletAccount.OwnerType.TEAM,
                                    teamId,
                                    LedgerEntry.Side.CREDIT,
                                    amountXu
                            )
                    ),
                    keyHash,
                    donationId,
                    donorId,
                    teamId
            ));
        } catch (InsufficientWalletBalanceException exception) {
            throw failure(
                    exception.getMessage(),
                    DonationException.Kind.INSUFFICIENT_BALANCE
            );
        } catch (LedgerConflictException exception) {
            throw failure(
                    exception.getMessage(),
                    DonationException.Kind.CONFLICT
            );
        }
        Instant now = clock.instant();
        Donation stored = repository.insert(new Donation(
                donationId,
                donorId,
                teamId,
                donorAccountId,
                teamAccountId,
                amountXu,
                normalizedMessage,
                posting.transaction().id(),
                keyHash,
                requestHash,
                Donation.Status.POSTED,
                now
        ));
        outbox.append(new IntegrationEvent(
                ids.get(),
                "monetization.donation.posted",
                1,
                now,
                donationId,
                "donation",
                donationId,
                donorId,
                teamId,
                new DonationPosted(teamId, amountXu)
        ));
        return receipt(stored, false);
    }

    private static LedgerEntry entry(
            WalletAccount.OwnerType ownerType,
            String ownerId,
            LedgerEntry.Side side,
            long amountXu
    ) {
        return new LedgerEntry(
                WalletOperations.accountId(ownerType, ownerId),
                side,
                amountXu
        );
    }

    private static String normalizeMessage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > 500
                || normalized.chars().anyMatch(
                character -> Character.isISOControl(character)
                        && character != '\n'
            )) {
            throw failure(
                    "Donation message is invalid.",
                    DonationException.Kind.INVALID
            );
        }
        return normalized;
    }

    private static String requireKey(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9._:-]{16,128}")) {
            throw failure(
                    "A valid Idempotency-Key is required.",
                    DonationException.Kind.INVALID
            );
        }
        return value;
    }

    private static String requireIdentity(String value, String message) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw failure(message, DonationException.Kind.INVALID);
        }
        return value;
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

    private static Receipt receipt(Donation value, boolean replayed) {
        return new Receipt(
                value.id(),
                value.teamId(),
                value.amountXu(),
                value.message(),
                value.ledgerTransactionId(),
                value.status().name(),
                replayed,
                value.createdAt()
        );
    }

    private static DonationException failure(
            String message,
            DonationException.Kind kind
    ) {
        return new DonationException(message, kind);
    }

    public record DonationPosted(String teamId, long amountXu) {
    }
}
