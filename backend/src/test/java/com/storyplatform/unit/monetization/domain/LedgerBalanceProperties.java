package com.storyplatform.unit.monetization.domain;

import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerBalanceProperties {

    private static final String DEBIT =
            "20000000-0000-4000-8000-000000000001";
    private static final String CREDIT =
            "30000000-0000-4000-8000-000000000001";

    @Property(tries = 250)
    void everyPositiveBalancedPostingPreservesItsIntegerAmount(
            @ForAll("positiveLongs") long amount
    ) {
        var transaction = posting(amount, amount);

        assertThat(transaction.amountXu()).isEqualTo(amount);
    }

    @Property(tries = 250)
    void everyUnequalPostingIsRejected(
            @ForAll("safePositiveLongs") long amount
    ) {
        assertThatThrownBy(() -> posting(amount, amount + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<Long> positiveLongs() {
        return net.jqwik.api.Arbitraries.longs()
                .between(1, Long.MAX_VALUE);
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<Long> safePositiveLongs() {
        return net.jqwik.api.Arbitraries.longs()
                .between(1, Long.MAX_VALUE - 1);
    }

    private static LedgerTransaction posting(long debit, long credit) {
        return LedgerTransaction.post(
                "10000000-0000-4000-8000-000000000001",
                LedgerTransaction.Type.ADJUSTMENT,
                "property",
                "property-01",
                List.of(
                        new LedgerEntry(
                                DEBIT,
                                LedgerEntry.Side.DEBIT,
                                debit
                        ),
                        new LedgerEntry(
                                CREDIT,
                                LedgerEntry.Side.CREDIT,
                                credit
                        )
                ),
                "a".repeat(64),
                Instant.EPOCH
        );
    }
}
