package com.storyplatform.unit.monetization.domain;

import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTransactionTest {

    private static final String TRANSACTION =
            "10000000-0000-4000-8000-000000000001";
    private static final String DEBIT =
            "20000000-0000-4000-8000-000000000001";
    private static final String CREDIT =
            "30000000-0000-4000-8000-000000000001";
    private static final String HASH = "a".repeat(64);

    @Test
    void postsBalancedPositiveIntegerXuEntries() {
        var transaction = transaction(List.of(
                entry(DEBIT, LedgerEntry.Side.DEBIT, 125_000),
                entry(CREDIT, LedgerEntry.Side.CREDIT, 125_000)
        ));

        assertThat(transaction.state())
                .isEqualTo(LedgerTransaction.State.POSTED);
        assertThat(transaction.amountXu()).isEqualTo(125_000);
        assertThat(transaction.entries()).isUnmodifiable();
    }

    @Test
    void rejectsImbalanceDuplicateAccountsAndOverflow() {
        assertThatThrownBy(() -> transaction(List.of(
                entry(DEBIT, LedgerEntry.Side.DEBIT, 2),
                entry(CREDIT, LedgerEntry.Side.CREDIT, 1)
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transaction(List.of(
                entry(DEBIT, LedgerEntry.Side.DEBIT, 1),
                entry(DEBIT, LedgerEntry.Side.CREDIT, 1)
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transaction(List.of(
                entry(DEBIT, LedgerEntry.Side.DEBIT, Long.MAX_VALUE),
                entry(CREDIT, LedgerEntry.Side.DEBIT, 1),
                entry("40000000-0000-4000-8000-000000000001",
                        LedgerEntry.Side.CREDIT, Long.MAX_VALUE)
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsAnImmutableCompensatingTransaction() {
        var original = transaction(List.of(
                entry(DEBIT, LedgerEntry.Side.DEBIT, 10),
                entry(CREDIT, LedgerEntry.Side.CREDIT, 10)
        ));

        var reversal = LedgerTransaction.reverse(
                "50000000-0000-4000-8000-000000000001",
                original,
                "donation_reversal",
                "reversal-01",
                "b".repeat(64),
                Instant.EPOCH.plusSeconds(1)
        );

        assertThat(reversal.type())
                .isEqualTo(LedgerTransaction.Type.REVERSAL);
        assertThat(reversal.compensatesTransactionId())
                .isEqualTo(original.id());
        assertThat(reversal.entries()).containsExactly(
                entry(DEBIT, LedgerEntry.Side.CREDIT, 10),
                entry(CREDIT, LedgerEntry.Side.DEBIT, 10)
        );
        assertThat(original.entries().getFirst().side())
                .isEqualTo(LedgerEntry.Side.DEBIT);
    }

    @Test
    void validatesEntryAndTransactionBoundaries() {
        assertThatThrownBy(() -> entry(
                "bad", LedgerEntry.Side.DEBIT, 1
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry(
                DEBIT, LedgerEntry.Side.DEBIT, 0
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LedgerTransaction.post(
                TRANSACTION,
                LedgerTransaction.Type.DONATION,
                "$where",
                "reference",
                List.of(
                        entry(DEBIT, LedgerEntry.Side.DEBIT, 1),
                        entry(CREDIT, LedgerEntry.Side.CREDIT, 1)
                ),
                "not-a-hash",
                Instant.EPOCH
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static LedgerTransaction transaction(List<LedgerEntry> entries) {
        return LedgerTransaction.post(
                TRANSACTION,
                LedgerTransaction.Type.DONATION,
                "donation",
                "donation-01",
                entries,
                HASH,
                Instant.EPOCH
        );
    }

    private static LedgerEntry entry(
            String account,
            LedgerEntry.Side side,
            long amount
    ) {
        return new LedgerEntry(account, side, amount);
    }
}
