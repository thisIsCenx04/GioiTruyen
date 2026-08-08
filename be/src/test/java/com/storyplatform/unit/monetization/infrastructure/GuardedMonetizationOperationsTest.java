package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.ManualTopupOperations;
import com.storyplatform.monetization.application
        .MonetizationKillSwitchGuard;
import com.storyplatform.monetization.application
        .MonetizationSuspendedException;
import com.storyplatform.monetization.application
        .TopupSettlementOperations;
import com.storyplatform.monetization.application.WithdrawalOperations;
import com.storyplatform.monetization.application
        .WithdrawalPayoutClaimOperations;
import com.storyplatform.monetization.application.port
        .MonetizationKillSwitchRepository;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;
import com.storyplatform.monetization.infrastructure
        .GuardedManualTopupOperations;
import com.storyplatform.monetization.infrastructure
        .GuardedTopupSettlementOperations;
import com.storyplatform.monetization.infrastructure
        .GuardedWithdrawalOperations;
import com.storyplatform.monetization.infrastructure
        .GuardedWithdrawalPayoutClaimOperations;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuardedMonetizationOperationsTest {

    private final MonetizationKillSwitchRepository repository =
            mock(MonetizationKillSwitchRepository.class);
    private final MonetizationKillSwitchGuard guard =
            new MonetizationKillSwitchGuard(repository);

    @Test
    void suspendedTopupStillAllowsEvidenceIngestPathToAcknowledge() {
        engage(MonetizationKillSwitch.Operation.TOPUP_CREDIT);
        var delegate = mock(TopupSettlementOperations.class);

        assertThat(new GuardedTopupSettlementOperations(
                delegate, guard
        ).settle("bank-provider", "event-001"))
                .isEqualTo(TopupSettlementOperations.Result.SUSPENDED);
        verify(delegate, never()).settle(
                "bank-provider", "event-001"
        );
    }

    @Test
    void blocksManualCreditAndWithdrawalCreationButNotHistory() {
        engage(MonetizationKillSwitch.Operation.TOPUP_CREDIT);
        engage(MonetizationKillSwitch.Operation.WITHDRAWAL_REQUEST);
        var manual = new GuardedManualTopupOperations(
                mock(ManualTopupOperations.class), guard
        );
        var withdrawalDelegate = mock(WithdrawalOperations.class);
        when(withdrawalDelegate.list("actor", "team", null, 20))
                .thenReturn(new WithdrawalOperations.Page(List.of(), null));
        var withdrawals = new GuardedWithdrawalOperations(
                withdrawalDelegate, guard
        );

        assertThatThrownBy(() -> manual.approve(
                "actor", "topup", "reauth", "reason", "evidence"
        )).isInstanceOf(MonetizationSuspendedException.class);
        assertThatThrownBy(() -> withdrawals.create(
                "actor", "team", "key", 100_000, "destination"
        )).isInstanceOf(MonetizationSuspendedException.class);
        assertThat(withdrawals.list(
                "actor", "team", null, 20
        ).items()).isEmpty();
    }

    @Test
    void payoutWorkerClaimsNothingWhileSwitchIsEngaged() {
        engage(MonetizationKillSwitch.Operation.WITHDRAWAL_PAYOUT);
        var delegate = mock(WithdrawalPayoutClaimOperations.class);

        assertThat(new GuardedWithdrawalPayoutClaimOperations(
                delegate, guard
        ).claim()).isEmpty();
        verify(delegate, never()).claim();
    }

    @Test
    void delegatesEveryOperationWhenSwitchesAreOpen() {
        var settlements = mock(TopupSettlementOperations.class);
        when(settlements.settle("bank-provider", "event-001"))
                .thenReturn(TopupSettlementOperations.Result.CREDITED);
        var claims = mock(WithdrawalPayoutClaimOperations.class);
        when(claims.claim()).thenReturn(Optional.empty());

        assertThat(new GuardedTopupSettlementOperations(
                settlements, guard
        ).settle("bank-provider", "event-001"))
                .isEqualTo(TopupSettlementOperations.Result.CREDITED);
        new GuardedWithdrawalPayoutClaimOperations(
                claims, guard
        ).claim();
        verify(claims).claim();
    }

    private void engage(MonetizationKillSwitch.Operation operation) {
        when(repository.find(operation)).thenReturn(Optional.of(
                new MonetizationKillSwitch(
                        operation,
                        true,
                        1,
                        "10000000-0000-4000-8000-000000000001",
                        java.time.Instant.parse(
                                "2026-07-25T01:00:00Z"
                        )
                )
        ));
    }
}
