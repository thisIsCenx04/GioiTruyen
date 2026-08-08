package com.storyplatform.monetization.application;

import com.storyplatform.analytics.application.contract
        .RewardViewAggregateDirectory;
import com.storyplatform.monetization.application.port.RewardRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.RewardPeriod;
import com.storyplatform.monetization.domain.RewardAdjustment;
import com.storyplatform.monetization.domain.RewardSettlement;
import com.storyplatform.monetization.domain.WalletAccount;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RewardService implements RewardOperations {

    public static final String READ_REWARDS = "finance:request";
    private static final String CLEARING_OWNER = "reward-clearing";
    private final RewardRepository repository;
    private final RewardViewAggregateDirectory aggregates;
    private final TeamPermissionAuthorizer permissions;
    private final WalletOperations wallets;
    private final LedgerOperations ledger;
    private final RewardRule rule;
    private final Clock clock;

    public RewardService(
            RewardRepository repository,
            RewardViewAggregateDirectory aggregates,
            TeamPermissionAuthorizer permissions,
            WalletOperations wallets,
            LedgerOperations ledger,
            RewardRule rule,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.aggregates = Objects.requireNonNull(aggregates);
        this.permissions = Objects.requireNonNull(permissions);
        this.wallets = Objects.requireNonNull(wallets);
        this.ledger = Objects.requireNonNull(ledger);
        this.rule = Objects.requireNonNull(rule);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public PeriodView settle(LocalDate periodDate) {
        Objects.requireNonNull(periodDate, "periodDate");
        Instant from = periodDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = periodDate.plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        if (to.isAfter(clock.instant())) {
            throw new RewardException(
                    "Reward periods can settle only after the UTC day closes.",
                    RewardException.Kind.INVALID
            );
        }
        String periodId = periodDate.toString();
        var existing = repository.findPeriod(periodId);
        boolean replayed = existing.isPresent()
                && existing.orElseThrow().state()
                == RewardPeriod.State.SETTLED;
        if (replayed) {
            return view(
                    existing.orElseThrow(),
                    repository.findByPeriod(periodId),
                    true
            );
        }
        RewardPeriod period = existing.orElseGet(() ->
                lock(periodDate, from, to));
        List<RewardSettlement> settlements =
                repository.findByPeriod(periodId);
        for (RewardSettlement settlement : settlements) {
            if (settlement.state() != RewardSettlement.State.PENDING) {
                continue;
            }
            if (settlement.amountXu() == 0) {
                if (!repository.markNoReward(
                        settlement.id(),
                        clock.instant()
                )) {
                    throw conflict();
                }
            } else {
                post(settlement);
            }
        }
        Instant settledAt = clock.instant();
        boolean marked = repository.markPeriodSettled(
                periodId,
                settledAt
        );
        RewardPeriod settled = repository.findPeriod(periodId)
                .filter(value -> value.state()
                        == RewardPeriod.State.SETTLED)
                .orElseGet(() -> {
                    if (!marked) {
                        throw conflict();
                    }
                    return period.settle(settledAt);
                });
        return view(
                settled,
                repository.findByPeriod(periodId),
                false
        );
    }

    @Override
    public AdjustmentView adjust(
            String settlementId,
            long correctedValidViews,
            String reasonCode,
            String idempotencyKey
    ) {
        String keyHash = hash(
                "reward-adjustment\n" + settlementId + "\n"
                        + requireKey(idempotencyKey)
        );
        var replay = repository.findAdjustmentByKeyHash(keyHash);
        if (replay.isPresent()) {
            RewardAdjustment value = replay.orElseThrow();
            if (value.correctedValidViews() != correctedValidViews
                    || !value.reasonCode().equals(reasonCode)) {
                throw conflict();
            }
            return view(value, true);
        }
        RewardSettlement settlement = repository.findSettlement(
                settlementId
        ).orElseThrow(() -> new RewardException(
                "Reward settlement was not found.",
                RewardException.Kind.INVALID
        ));
        if (repository.findAdjustmentBySettlement(settlementId)
                .isPresent()) {
            throw conflict();
        }
        RewardPeriod period = repository.findPeriod(settlement.periodId())
                .filter(value -> value.state()
                        == RewardPeriod.State.SETTLED)
                .orElseThrow(RewardService::conflict);
        RewardRule snapshottedRule = new RewardRule(
                period.ruleVersion(),
                period.xuPerThousandValidViews(),
                period.teamCapXu()
        );
        long correctedAmount = snapshottedRule
                .calculate(correctedValidViews)
                .amountXu();
        long delta = correctedAmount - settlement.amountXu();
        if (delta == 0 || reasonCode == null
                || !reasonCode.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw new RewardException(
                    "Reward correction must change the amount "
                            + "and include a valid reason.",
                    RewardException.Kind.INVALID
            );
        }
        long amount = Math.abs(delta);
        wallets.open(WalletAccount.OwnerType.PLATFORM, CLEARING_OWNER);
        wallets.open(
                WalletAccount.OwnerType.TEAM,
                settlement.teamId()
        );
        LedgerEntry.Side platformSide = delta > 0
                ? LedgerEntry.Side.DEBIT
                : LedgerEntry.Side.CREDIT;
        LedgerEntry.Side teamSide = delta > 0
                ? LedgerEntry.Side.CREDIT
                : LedgerEntry.Side.DEBIT;
        LedgerOperations.Posting posting;
        try {
            posting = ledger.post(new LedgerOperations.Command(
                    LedgerTransaction.Type.ADJUSTMENT,
                    "reward_adjustment",
                    UUID.nameUUIDFromBytes(keyHash.getBytes(
                            StandardCharsets.US_ASCII
                    )).toString(),
                    List.of(
                            new LedgerEntry(
                                    WalletOperations.accountId(
                                            WalletAccount.OwnerType
                                                    .PLATFORM,
                                            CLEARING_OWNER
                                    ),
                                    platformSide,
                                    amount
                            ),
                            new LedgerEntry(
                                    settlement.teamAccountId(),
                                    teamSide,
                                    amount
                            )
                    ),
                    keyHash,
                    settlement.id(),
                    "system:reward-correction",
                    settlement.teamId()
            ));
        } catch (InsufficientWalletBalanceException
                 | LedgerConflictException exception) {
            throw conflict();
        }
        RewardAdjustment adjustment = repository.insertAdjustment(
                new RewardAdjustment(
                        UUID.nameUUIDFromBytes((
                                "gioitruyen:reward-adjustment:" + keyHash
                        ).getBytes(StandardCharsets.US_ASCII)).toString(),
                        settlement.id(),
                        correctedValidViews,
                        settlement.amountXu(),
                        correctedAmount,
                        delta,
                        reasonCode,
                        keyHash,
                        posting.transaction().id(),
                        clock.instant()
                )
        );
        return view(adjustment, false);
    }

    @Override
    public List<SettlementView> recent(
            String actorId,
            String teamId,
            int limit
    ) {
        if (teamId == null || teamId.isBlank()
                || limit < 1 || limit > 100) {
            throw new RewardException(
                    "Reward history request is invalid.",
                    RewardException.Kind.INVALID
            );
        }
        if (!permissions.allows(actorId, teamId, READ_REWARDS)) {
            throw new RewardException(
                    "The finance:request Team permission is required.",
                    RewardException.Kind.FORBIDDEN
            );
        }
        return repository.findRecentByTeam(teamId, limit).stream()
                .map(RewardService::view)
                .toList();
    }

    private RewardPeriod lock(
            LocalDate date,
            Instant from,
            Instant to
    ) {
        List<RewardViewAggregateDirectory.TeamValidViews> inputs =
                aggregates.validViewsByTeam(from, to);
        Instant now = clock.instant();
        long totalViews = inputs.stream().mapToLong(
                RewardViewAggregateDirectory.TeamValidViews::validViews
        ).reduce(0, Math::addExact);
        RewardPeriod period = new RewardPeriod(
                date.toString(), date, from, to, rule.version(),
                rule.xuPerThousandValidViews(), rule.teamCapXu(),
                aggregates.aggregateVersion(), inputs.size(), totalViews,
                RewardPeriod.State.LOCKED, now, null
        );
        List<RewardSettlement> settlements = inputs.stream()
                .map(input -> pending(period, input, now))
                .toList();
        return repository.lock(period, settlements).period();
    }

    private RewardSettlement pending(
            RewardPeriod period,
            RewardViewAggregateDirectory.TeamValidViews input,
            Instant now
    ) {
        var calculation = rule.calculate(input.validViews());
        return new RewardSettlement(
                UUID.nameUUIDFromBytes((
                        "gioitruyen:reward:" + period.id()
                                + ":" + input.teamId()
                ).getBytes(StandardCharsets.UTF_8)).toString(),
                period.id(),
                input.teamId(),
                WalletOperations.accountId(
                        WalletAccount.OwnerType.TEAM,
                        input.teamId()
                ),
                input.validViews(),
                calculation.amountXu(),
                calculation.capApplied(),
                period.ruleVersion(),
                period.aggregateVersion(),
                null,
                RewardSettlement.State.PENDING,
                now,
                null
        );
    }

    private void post(RewardSettlement settlement) {
        wallets.open(WalletAccount.OwnerType.PLATFORM, CLEARING_OWNER);
        wallets.open(WalletAccount.OwnerType.TEAM, settlement.teamId());
        String clearing = WalletOperations.accountId(
                WalletAccount.OwnerType.PLATFORM,
                CLEARING_OWNER
        );
        var posting = ledger.post(new LedgerOperations.Command(
                LedgerTransaction.Type.REWARD,
                "reward_settlement",
                settlement.id(),
                List.of(
                        new LedgerEntry(
                                clearing,
                                LedgerEntry.Side.DEBIT,
                                settlement.amountXu()
                        ),
                        new LedgerEntry(
                                settlement.teamAccountId(),
                                LedgerEntry.Side.CREDIT,
                                settlement.amountXu()
                        )
                ),
                hash("reward\n" + settlement.id()),
                settlement.id(),
                "system:reward-settlement",
                settlement.teamId()
        ));
        if (!repository.markPosted(
                settlement.id(),
                posting.transaction().id(),
                clock.instant()
        )) {
            throw conflict();
        }
    }

    private static PeriodView view(
            RewardPeriod period,
            List<RewardSettlement> settlements,
            boolean replayed
    ) {
        long rewarded = settlements.stream()
                .mapToLong(RewardSettlement::amountXu)
                .reduce(0, Math::addExact);
        return new PeriodView(
                period.id(), period.state().name(), period.ruleVersion(),
                period.aggregateVersion(), period.teamCount(),
                period.validViews(), rewarded, period.lockedAt(),
                period.settledAt(), replayed
        );
    }

    private static SettlementView view(RewardSettlement value) {
        return new SettlementView(
                value.id(), value.periodId(), value.validViews(),
                value.amountXu(), value.capApplied(), value.ruleVersion(),
                value.aggregateVersion(), value.state().name(),
                value.createdAt(), value.postedAt()
        );
    }

    private static AdjustmentView view(
            RewardAdjustment value,
            boolean replayed
    ) {
        return new AdjustmentView(
                value.id(), value.settlementId(),
                value.correctedValidViews(), value.previousAmountXu(),
                value.correctedAmountXu(), value.deltaXu(),
                value.reasonCode(), value.ledgerTransactionId(),
                value.createdAt(), replayed
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

    private static String requireKey(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9._:-]{16,128}")) {
            throw new RewardException(
                    "A valid Idempotency-Key is required.",
                    RewardException.Kind.INVALID
            );
        }
        return value;
    }

    private static RewardException conflict() {
        return new RewardException(
                "Reward settlement changed concurrently; retry safely.",
                RewardException.Kind.CONFLICT
        );
    }
}
