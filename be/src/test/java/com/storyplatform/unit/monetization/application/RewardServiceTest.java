package com.storyplatform.unit.monetization.application;

import com.storyplatform.analytics.application.contract
        .RewardViewAggregateDirectory;
import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application.RewardException;
import com.storyplatform.monetization.application.RewardRule;
import com.storyplatform.monetization.application.RewardService;
import com.storyplatform.monetization.application.WalletOperations;
import com.storyplatform.monetization.application.port.RewardRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.RewardPeriod;
import com.storyplatform.monetization.domain.RewardAdjustment;
import com.storyplatform.monetization.domain.RewardSettlement;
import com.storyplatform.monetization.domain.WalletAccount;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RewardServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 7, 24);
    private final RewardRepository repository =
            mock(RewardRepository.class);
    private final RewardViewAggregateDirectory aggregates =
            mock(RewardViewAggregateDirectory.class);
    private final TeamPermissionAuthorizer permissions =
            mock(TeamPermissionAuthorizer.class);
    private final WalletOperations wallets = mock(WalletOperations.class);
    private final LedgerOperations ledger = mock(LedgerOperations.class);
    private final RewardRule rule = new RewardRule(
            "reward-2026.1", 100, 150
    );
    private final RewardService service = new RewardService(
            repository,
            aggregates,
            permissions,
            wallets,
            ledger,
            rule,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void locksValidViewsPostsCappedRewardAndClosesZeroReward() {
        when(aggregates.aggregateVersion())
                .thenReturn("view-aggregate-2026.1");
        when(aggregates.validViewsByTeam(any(), any())).thenReturn(List.of(
                new RewardViewAggregateDirectory.TeamValidViews("team-a", 5),
                new RewardViewAggregateDirectory.TeamValidViews(
                        "team-b",
                        2_000
                )
        ));
        when(repository.findPeriod(DATE.toString()))
                .thenReturn(Optional.empty());
        when(repository.lock(any(), any())).thenAnswer(invocation ->
                new RewardRepository.LockResult(
                        invocation.getArgument(0),
                        true
                ));
        RewardSettlement zero = settlement(
                "team-a", 5, 0, false,
                RewardSettlement.State.PENDING, null
        );
        RewardSettlement capped = settlement(
                "team-b", 2_000, 150, true,
                RewardSettlement.State.PENDING, null
        );
        RewardSettlement closed = zero.noReward(NOW);
        RewardSettlement posted = capped.posted(
                "30000000-0000-4000-8000-000000000001",
                NOW
        );
        when(repository.findByPeriod(DATE.toString()))
                .thenReturn(
                        List.of(zero, capped),
                        List.of(closed, posted)
                );
        when(repository.markNoReward(any(), any())).thenReturn(true);
        when(repository.markPosted(any(), any(), any()))
                .thenReturn(true);
        when(repository.markPeriodSettled(any(), any()))
                .thenReturn(true);
        when(ledger.post(any())).thenReturn(posting(capped));
        ArgumentCaptor<LedgerOperations.Command> command =
                ArgumentCaptor.forClass(LedgerOperations.Command.class);

        var result = service.settle(DATE);

        assertThat(result.state()).isEqualTo("SETTLED");
        assertThat(result.validViews()).isEqualTo(2_005);
        assertThat(result.rewardedXu()).isEqualTo(150);
        assertThat(result.teamCount()).isEqualTo(2);
        verify(ledger).post(command.capture());
        assertThat(command.getValue().entries())
                .extracting(LedgerEntry::side)
                .containsExactly(
                        LedgerEntry.Side.DEBIT,
                        LedgerEntry.Side.CREDIT
                );
        verify(repository).markNoReward(zero.id(), NOW);
    }

    @Test
    void settledPeriodReplaysWithoutReadingMutableAggregates() {
        RewardPeriod settled = period().settle(NOW);
        when(repository.findPeriod(DATE.toString()))
                .thenReturn(Optional.of(settled));
        when(repository.findByPeriod(DATE.toString()))
                .thenReturn(List.of());

        assertThat(service.settle(DATE).replayed()).isTrue();
        verify(aggregates, never()).validViewsByTeam(any(), any());
        verify(ledger, never()).post(any());
    }

    @Test
    void rejectsOpenPeriodAndUnauthorizedHistory() {
        assertThatThrownBy(() ->
                service.settle(LocalDate.of(2026, 7, 25))
        ).isInstanceOf(RewardException.class)
                .extracting("kind")
                .isEqualTo(RewardException.Kind.INVALID);
        assertThatThrownBy(() ->
                service.recent("actor", "team-a", 24)
        ).isInstanceOf(RewardException.class)
                .extracting("kind")
                .isEqualTo(RewardException.Kind.FORBIDDEN);
    }

    @Test
    void returnsAuthorizedVersionedHistory() {
        when(permissions.allows(
                "actor",
                "team-a",
                RewardService.READ_REWARDS
        )).thenReturn(true);
        when(repository.findRecentByTeam("team-a", 24))
                .thenReturn(List.of(settlement(
                        "team-a", 1_000, 100, false,
                        RewardSettlement.State.POSTED,
                        "30000000-0000-4000-8000-000000000001"
                )));

        assertThat(service.recent("actor", "team-a", 24))
                .singleElement()
                .satisfies(value -> {
                    assertThat(value.ruleVersion())
                            .isEqualTo("reward-2026.1");
                    assertThat(value.validViews()).isEqualTo(1_000);
                });
    }

    @Test
    void correctionPostsCompensatingAdjustmentFromSnapshottedRule() {
        RewardSettlement original = settlement(
                "team-a", 1_000, 100, false,
                RewardSettlement.State.POSTED,
                "30000000-0000-4000-8000-000000000001"
        );
        when(repository.findSettlement(original.id()))
                .thenReturn(Optional.of(original));
        when(repository.findPeriod(DATE.toString()))
                .thenReturn(Optional.of(period().settle(NOW)));
        when(ledger.post(any())).thenAnswer(invocation -> {
            LedgerOperations.Command command = invocation.getArgument(0);
            return new LedgerOperations.Posting(
                    LedgerTransaction.post(
                            "40000000-0000-4000-8000-000000000001",
                            command.type(),
                            command.referenceType(),
                            command.referenceId(),
                            command.entries(),
                            command.idempotencyKeyHash(),
                            NOW
                    ),
                    false
            );
        });
        when(repository.insertAdjustment(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );
        ArgumentCaptor<LedgerOperations.Command> command =
                ArgumentCaptor.forClass(LedgerOperations.Command.class);

        var result = service.adjust(
                original.id(),
                500,
                "FRAUD_RECONCILIATION",
                "reward-adjust-key-0001"
        );

        assertThat(result.deltaXu()).isEqualTo(-50);
        assertThat(result.correctedAmountXu()).isEqualTo(50);
        verify(ledger).post(command.capture());
        assertThat(command.getValue().type())
                .isEqualTo(LedgerTransaction.Type.ADJUSTMENT);
        assertThat(command.getValue().entries())
                .extracting(LedgerEntry::side)
                .containsExactly(
                        LedgerEntry.Side.CREDIT,
                        LedgerEntry.Side.DEBIT
                );
    }

    @Test
    void correctionReplaysSameKeyAndRejectsChangedPayload() {
        RewardAdjustment existing = new RewardAdjustment(
                "50000000-0000-4000-8000-000000000001",
                "60000000-0000-4000-8000-000000000001",
                500,
                100,
                50,
                -50,
                "FRAUD_RECONCILIATION",
                adjustmentKeyHash(
                        "60000000-0000-4000-8000-000000000001",
                        "reward-adjust-key-0001"
                ),
                "70000000-0000-4000-8000-000000000001",
                NOW
        );
        when(repository.findAdjustmentByKeyHash(
                existing.idempotencyKeyHash()
        )).thenReturn(Optional.of(existing));

        assertThat(service.adjust(
                existing.settlementId(),
                500,
                "FRAUD_RECONCILIATION",
                "reward-adjust-key-0001"
        ).replayed()).isTrue();
        assertThatThrownBy(() -> service.adjust(
                existing.settlementId(),
                501,
                "FRAUD_RECONCILIATION",
                "reward-adjust-key-0001"
        )).isInstanceOf(RewardException.class)
                .extracting("kind")
                .isEqualTo(RewardException.Kind.CONFLICT);
        verify(ledger, never()).post(any());
    }

    private static RewardPeriod period() {
        Instant from = DATE.atStartOfDay(ZoneOffset.UTC).toInstant();
        return new RewardPeriod(
                DATE.toString(), DATE, from,
                DATE.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                "reward-2026.1", 100, 150,
                "view-aggregate-2026.1", 2, 2_005,
                RewardPeriod.State.LOCKED, NOW.minusSeconds(60), null
        );
    }

    private static RewardSettlement settlement(
            String teamId,
            long views,
            long amount,
            boolean capped,
            RewardSettlement.State state,
            String ledgerId
    ) {
        String id = java.util.UUID.nameUUIDFromBytes(
                (DATE + ":" + teamId).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                )
        ).toString();
        return new RewardSettlement(
                id,
                DATE.toString(),
                teamId,
                WalletOperations.accountId(
                        WalletAccount.OwnerType.TEAM,
                        teamId
                ),
                views,
                amount,
                capped,
                "reward-2026.1",
                "view-aggregate-2026.1",
                ledgerId,
                state,
                NOW.minusSeconds(60),
                state == RewardSettlement.State.PENDING ? null : NOW
        );
    }

    private static LedgerOperations.Posting posting(
            RewardSettlement settlement
    ) {
        return new LedgerOperations.Posting(
                LedgerTransaction.post(
                        "30000000-0000-4000-8000-000000000001",
                        LedgerTransaction.Type.REWARD,
                        "reward_settlement",
                        settlement.id(),
                        List.of(
                                new LedgerEntry(
                                        WalletOperations.accountId(
                                                WalletAccount.OwnerType
                                                        .PLATFORM,
                                                "reward-clearing"
                                        ),
                                        LedgerEntry.Side.DEBIT,
                                        settlement.amountXu()
                                ),
                                new LedgerEntry(
                                        settlement.teamAccountId(),
                                        LedgerEntry.Side.CREDIT,
                                        settlement.amountXu()
                                )
                        ),
                        "a".repeat(64),
                        NOW
                ),
                false
        );
    }

    private static String adjustmentKeyHash(
            String settlementId,
            String key
    ) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest((
                                    "reward-adjustment\n"
                                            + settlementId + "\n" + key
                            ).getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8
                            ))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
