package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application
        .InsufficientWalletBalanceException;
import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application.WalletOperations;
import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application.WithdrawalFeePolicy;
import com.storyplatform.monetization.application.WithdrawalService;
import com.storyplatform.monetization.application.port
        .WithdrawalCursorCodec;
import com.storyplatform.monetization.application.port
        .WithdrawalDestinationDirectory;
import com.storyplatform.monetization.application.port.WithdrawalRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.Withdrawal;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawalServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");
    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String DESTINATION =
            "30000000-0000-4000-8000-000000000001";
    private static final String WITHDRAWAL =
            "40000000-0000-4000-8000-000000000001";
    private static final String LEDGER =
            "50000000-0000-4000-8000-000000000001";
    private static final String EVENT =
            "60000000-0000-4000-8000-000000000001";
    private static final String KEY = "withdrawal-key-0001";
    private final WithdrawalRepository repository =
            mock(WithdrawalRepository.class);
    private final WithdrawalDestinationDirectory destinations =
            mock(WithdrawalDestinationDirectory.class);
    private final WithdrawalCursorCodec cursors =
            mock(WithdrawalCursorCodec.class);
    private final TeamPermissionAuthorizer permissions =
            mock(TeamPermissionAuthorizer.class);
    private final WalletOperations wallets = mock(WalletOperations.class);
    private final LedgerOperations ledger = mock(LedgerOperations.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);

    @BeforeEach
    void setUp() {
        when(permissions.allows(
                ACTOR,
                TEAM,
                WithdrawalService.REQUEST_WITHDRAWAL
        )).thenReturn(true);
        when(destinations.findEligible(TEAM, DESTINATION, NOW))
                .thenReturn(Optional.of(destination()));
    }

    @Test
    void reservesGrossAvailableXuAndReplaysSameRequest() {
        AtomicReference<Withdrawal> stored = new AtomicReference<>();
        when(repository.findByIdempotencyKeyHash(any()))
                .thenAnswer(invocation ->
                        Optional.ofNullable(stored.get()));
        when(repository.insert(any())).thenAnswer(invocation -> {
            Withdrawal value = invocation.getArgument(0);
            stored.set(value);
            return value;
        });
        when(ledger.post(any())).thenAnswer(invocation ->
                posting(invocation.getArgument(0)));
        WithdrawalService service = service();
        ArgumentCaptor<LedgerOperations.Command> command =
                ArgumentCaptor.forClass(LedgerOperations.Command.class);

        var created = service.create(
                ACTOR, TEAM, KEY, 100_000, DESTINATION
        );
        var replayed = service.create(
                ACTOR, TEAM, KEY, 100_000, DESTINATION
        );

        assertThat(created.replayed()).isFalse();
        assertThat(replayed.replayed()).isTrue();
        assertThat(created.destinationMasked())
                .isEqualTo("VCB •••• 1234");
        assertThat(created.feeXu()).isEqualTo(20_000);
        assertThat(created.netAmountXu()).isEqualTo(80_000);
        assertThat(created.feeRuleVersion())
                .isEqualTo("withdrawal-fee-2026.1");
        verify(ledger).post(command.capture());
        assertThat(command.getValue().type())
                .isEqualTo(LedgerTransaction.Type.WITHDRAWAL_RESERVE);
        assertThat(command.getValue().entries())
                .extracting(LedgerEntry::side, LedgerEntry::bucket)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                LedgerEntry.Side.DEBIT,
                                LedgerEntry.Bucket.AVAILABLE
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                LedgerEntry.Side.CREDIT,
                                LedgerEntry.Bucket.RESERVED
                        )
                );
    }

    @Test
    void rejectsUnauthorizedInvalidDestinationAndInsufficientBalance() {
        WithdrawalService service = service();
        when(permissions.allows(
                ACTOR,
                TEAM,
                WithdrawalService.REQUEST_WITHDRAWAL
        )).thenReturn(false);
        assertKind(
                () -> service.create(
                        ACTOR, TEAM, KEY, 100_000, DESTINATION
                ),
                WithdrawalException.Kind.FORBIDDEN
        );
        verify(repository, never()).findByIdempotencyKeyHash(any());

        when(permissions.allows(
                ACTOR,
                TEAM,
                WithdrawalService.REQUEST_WITHDRAWAL
        )).thenReturn(true);
        when(destinations.findEligible(TEAM, DESTINATION, NOW))
                .thenReturn(Optional.empty());
        assertKind(
                () -> service.create(
                        ACTOR, TEAM, KEY, 100_000, DESTINATION
                ),
                WithdrawalException.Kind.DESTINATION_UNAVAILABLE
        );

        when(destinations.findEligible(TEAM, DESTINATION, NOW))
                .thenReturn(Optional.of(destination()));
        doThrow(new InsufficientWalletBalanceException())
                .when(ledger).post(any());
        assertKind(
                () -> service.create(
                        ACTOR, TEAM, KEY, 100_000, DESTINATION
                ),
                WithdrawalException.Kind.INSUFFICIENT_BALANCE
        );
    }

    @Test
    void rejectsAmountsKeysAndChangedIdempotentPayload() {
        WithdrawalService service = service();
        assertKind(
                () -> service.create(
                        ACTOR, TEAM, KEY, 99_999, DESTINATION
                ),
                WithdrawalException.Kind.INVALID
        );
        assertKind(
                () -> service.create(
                        ACTOR, TEAM, "short", 100_000, DESTINATION
                ),
                WithdrawalException.Kind.INVALID
        );
        Withdrawal existing = withdrawal(NOW);
        when(repository.findByIdempotencyKeyHash(any()))
                .thenReturn(Optional.of(existing));
        assertKind(
                () -> service.create(
                        ACTOR, TEAM, KEY, 100_001, DESTINATION
                ),
                WithdrawalException.Kind.CONFLICT
        );
    }

    @Test
    void listsWithSignedKeysetCursorAndBoundsLimit() {
        Withdrawal first = withdrawal(NOW);
        Withdrawal second = new Withdrawal(
                "70000000-0000-4000-8000-000000000001",
                TEAM,
                first.accountId(),
                200_000,
                20_000,
                180_000,
                "withdrawal-fee-2026.1",
                destination(),
                Withdrawal.State.PENDING_REVIEW,
                ACTOR,
                LEDGER,
                "c".repeat(64),
                "d".repeat(64),
                NOW.minusSeconds(1)
        );
        when(repository.findByTeam(TEAM, null, 2))
                .thenReturn(List.of(first, second));
        when(cursors.encode(any())).thenReturn("next");
        WithdrawalService service = service();

        var page = service.list(ACTOR, TEAM, null, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.nextCursor()).isEqualTo("next");
        assertKind(
                () -> service.list(ACTOR, TEAM, null, 101),
                WithdrawalException.Kind.INVALID
        );
        when(cursors.decode("tampered")).thenReturn(Optional.empty());
        assertKind(
                () -> service.list(ACTOR, TEAM, "tampered", 20),
                WithdrawalException.Kind.INVALID
        );
    }

    private WithdrawalService service() {
        Iterator<UUID> ids = List.of(
                UUID.fromString(WITHDRAWAL),
                UUID.fromString(EVENT)
        ).iterator();
        return new WithdrawalService(
                repository,
                destinations,
                cursors,
                permissions,
                wallets,
                ledger,
                outbox,
                WithdrawalFeePolicy.defaults(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                ids::next
        );
    }

    private static LedgerOperations.Posting posting(
            LedgerOperations.Command command
    ) {
        return new LedgerOperations.Posting(
                LedgerTransaction.post(
                        LEDGER,
                        command.type(),
                        command.referenceType(),
                        command.referenceId(),
                        command.entries(),
                        command.idempotencyKeyHash(),
                        NOW
                ),
                false
        );
    }

    private static Withdrawal.DestinationSnapshot destination() {
        return new Withdrawal.DestinationSnapshot(
                DESTINATION,
                "VCB •••• 1234",
                "encrypted:v1:ciphertext",
                2,
                NOW.minusSeconds(86_400)
        );
    }

    private static Withdrawal withdrawal(Instant createdAt) {
        return new Withdrawal(
                WITHDRAWAL,
                TEAM,
                WalletOperations.accountId(
                        com.storyplatform.monetization.domain.WalletAccount
                                .OwnerType.TEAM,
                        TEAM
                ),
                100_000,
                20_000,
                80_000,
                "withdrawal-fee-2026.1",
                destination(),
                Withdrawal.State.PENDING_REVIEW,
                ACTOR,
                LEDGER,
                "a".repeat(64),
                "b".repeat(64),
                createdAt
        );
    }

    private static void assertKind(
            Runnable operation,
            WithdrawalException.Kind kind
    ) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(WithdrawalException.class)
                .extracting("kind")
                .isEqualTo(kind);
    }
}
