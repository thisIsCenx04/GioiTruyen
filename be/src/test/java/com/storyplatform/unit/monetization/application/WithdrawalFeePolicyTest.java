package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application.WithdrawalFeePolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalFeePolicyTest {

    private final WithdrawalFeePolicy policy =
            WithdrawalFeePolicy.defaults();

    @ParameterizedTest
    @CsvSource({
            "100000, 20000, 80000",
            "999999, 20000, 979999",
            "1000000, 0, 1000000",
            "1000001, 0, 1000001"
    })
    void calculatesEveryRequiredBoundaryServerSide(
            long gross,
            long fee,
            long net
    ) {
        var quote = policy.quote(gross);

        assertThat(quote.feeXu()).isEqualTo(fee);
        assertThat(quote.netAmountXu()).isEqualTo(net);
        assertThat(quote.feeXu() + quote.netAmountXu())
                .isEqualTo(gross);
        assertThat(quote.version())
                .isEqualTo("withdrawal-fee-2026.1");
    }

    @ParameterizedTest
    @CsvSource({"0", "99999", "1000000001", "9223372036854775807"})
    void rejectsAmountsOutsideSafeBoundaries(long gross) {
        assertThatThrownBy(() -> policy.quote(gross))
                .isInstanceOf(WithdrawalException.class)
                .extracting("kind")
                .isEqualTo(WithdrawalException.Kind.INVALID);
    }

    @org.junit.jupiter.api.Test
    void preservesGrossEqualsFeePlusNetAcrossBoundaryNeighborhoods() {
        LongStream.concat(
                LongStream.rangeClosed(100_000, 100_100),
                LongStream.rangeClosed(999_900, 1_000_100)
        ).mapToObj(policy::quote).forEach(quote ->
                assertThat(Math.addExact(
                        quote.feeXu(),
                        quote.netAmountXu()
                )).isEqualTo(quote.grossAmountXu())
        );
    }

    @org.junit.jupiter.api.Test
    void rejectsEachInconsistentPolicyConfiguration() {
        assertInvalidPolicy(null, 100_000, 1_000_000, 20_000, 1_000_000);
        assertInvalidPolicy("BAD", 100_000, 1_000_000, 20_000, 1_000_000);
        assertInvalidPolicy("fee-v1", 0, 1_000_000, 20_000, 1_000_000);
        assertInvalidPolicy(
                "fee-v1", 100_000, 1_000_000, -1, 1_000_000
        );
        assertInvalidPolicy(
                "fee-v1", 100_000, 1_000_000, 100_000, 1_000_000
        );
        assertInvalidPolicy(
                "fee-v1", 100_000, 100_000, 20_000, 1_000_000
        );
        assertInvalidPolicy(
                "fee-v1", 100_000, 1_000_000, 20_000, 999_999
        );
    }

    @org.junit.jupiter.api.Test
    void rejectsEachInconsistentQuoteInvariant() {
        assertInvalidQuote(0, 0, 0, "fee-v1");
        assertInvalidQuote(100_000, -1, 100_001, "fee-v1");
        assertInvalidQuote(100_000, 100_001, 1, "fee-v1");
        assertInvalidQuote(100_000, 20_000, 0, "fee-v1");
        assertInvalidQuote(100_000, 20_000, 79_999, "fee-v1");
        assertInvalidQuote(100_000, 20_000, 80_000, null);
    }

    private static void assertInvalidPolicy(
            String version,
            long minimum,
            long freeFrom,
            long fee,
            long maximum
    ) {
        assertThatThrownBy(() -> new WithdrawalFeePolicy(
                version, minimum, freeFrom, fee, maximum
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertInvalidQuote(
            long gross,
            long fee,
            long net,
            String version
    ) {
        assertThatThrownBy(() -> new WithdrawalFeePolicy.Quote(
                gross, fee, net, version
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
