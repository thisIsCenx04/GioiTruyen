package com.storyplatform.unit.monetization.domain;

import com.storyplatform.monetization.application.WithdrawalFeePolicy;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.TopupRequest;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MonetizationAmountInvariantProperties {

    private static final String TRANSACTION =
            "10000000-0000-4000-8000-000000000001";
    private static final String REVERSAL =
            "10000000-0000-4000-8000-000000000002";
    private static final String SOURCE =
            "20000000-0000-4000-8000-000000000001";
    private static final String DESTINATION =
            "30000000-0000-4000-8000-000000000001";

    @Property(tries = 500)
    void topupSnapshotAlwaysFloorsWithoutCreatingXu(
            @ForAll
            @LongRange(
                    min = TopupRequest.MINIMUM_AMOUNT_VND,
                    max = TopupRequest.MAXIMUM_AMOUNT_VND
            )
            long amountVnd,
            @ForAll @IntRange(min = 0, max = 10_000) int basisPoints
    ) {
        BigDecimal discount = BigDecimal.valueOf(basisPoints, 2);

        long credited = TopupRequest.calculateCreditedXu(
                amountVnd,
                discount
        );
        long independentlyCalculated = BigDecimal.valueOf(amountVnd)
                .multiply(BigDecimal.valueOf(100).subtract(discount))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
                .longValueExact();

        assertThat(credited).isEqualTo(independentlyCalculated);
        assertThat(credited).isBetween(0L, amountVnd);
    }

    @Property(tries = 500)
    void withdrawalQuoteConservesGrossAcrossFeeBoundary(
            @ForAll
            @LongRange(min = 100_000, max = 1_000_000_000)
            long grossXu
    ) {
        WithdrawalFeePolicy.Quote quote =
                WithdrawalFeePolicy.defaults().quote(grossXu);

        assertThat(Math.addExact(quote.feeXu(), quote.netAmountXu()))
                .isEqualTo(grossXu);
        assertThat(quote.feeXu())
                .isEqualTo(grossXu < 1_000_000 ? 20_000 : 0);
        assertThat(quote.netAmountXu()).isPositive();
    }

    @Property(tries = 500)
    void compensatingTransactionNeutralizesEveryAccountBucket(
            @ForAll
            @LongRange(min = 1, max = Long.MAX_VALUE)
            long amountXu
    ) {
        LedgerTransaction original = LedgerTransaction.post(
                TRANSACTION,
                LedgerTransaction.Type.DONATION,
                "donation",
                "donation-01",
                List.of(
                        new LedgerEntry(
                                SOURCE,
                                LedgerEntry.Side.DEBIT,
                                LedgerEntry.Bucket.AVAILABLE,
                                amountXu
                        ),
                        new LedgerEntry(
                                DESTINATION,
                                LedgerEntry.Side.CREDIT,
                                LedgerEntry.Bucket.AVAILABLE,
                                amountXu
                        )
                ),
                "a".repeat(64),
                Instant.EPOCH
        );
        LedgerTransaction reversal = LedgerTransaction.reverse(
                REVERSAL,
                original,
                "donation_reversal",
                "donation-01",
                "b".repeat(64),
                Instant.EPOCH.plusSeconds(1)
        );

        Map<String, Long> netByAccountBucket = new HashMap<>();
        for (LedgerEntry entry : concat(
                original.entries(),
                reversal.entries()
        )) {
            String key = entry.accountId() + ":" + entry.bucket();
            long signed = entry.side() == LedgerEntry.Side.DEBIT
                    ? -entry.amountXu()
                    : entry.amountXu();
            netByAccountBucket.merge(key, signed, Math::addExact);
        }

        assertThat(reversal.amountXu()).isEqualTo(original.amountXu());
        assertThat(reversal.compensatesTransactionId())
                .isEqualTo(original.id());
        assertThat(netByAccountBucket.values()).containsOnly(0L);
    }

    private static List<LedgerEntry> concat(
            List<LedgerEntry> first,
            List<LedgerEntry> second
    ) {
        return java.util.stream.Stream.concat(
                first.stream(),
                second.stream()
        ).toList();
    }
}
