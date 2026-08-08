package com.storyplatform.unit.monetization.domain;

import com.storyplatform.monetization.domain.Donation;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DonationTest {

    @Test
    void acceptsBoundaryAmountsAndCompleteLedgerReferences() {
        Donation minimum = donation(1, null);
        Donation maximum = donation(
                Donation.MAXIMUM_AMOUNT_XU,
                "x".repeat(500)
        );

        assertThat(minimum.amountXu()).isOne();
        assertThat(maximum.amountXu())
                .isEqualTo(Donation.MAXIMUM_AMOUNT_XU);
        assertThat(minimum.donorAccountId()).isNotBlank();
        assertThat(minimum.teamAccountId()).isNotBlank();
    }

    @Test
    void rejectsInvalidAmountsAndMessage() {
        assertThatThrownBy(() -> donation(0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> donation(
                Donation.MAXIMUM_AMOUNT_XU + 1,
                null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> donation(1, "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidIdentityHashAndRequiredFields() {
        assertThatThrownBy(() -> new Donation(
                "not-a-uuid",
                "reader",
                "team",
                "10000000-0000-4000-8000-000000000002",
                "10000000-0000-4000-8000-000000000003",
                1,
                null,
                "10000000-0000-4000-8000-000000000004",
                "a".repeat(64),
                "b".repeat(64),
                Donation.Status.POSTED,
                Instant.EPOCH
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Donation(
                "10000000-0000-4000-8000-000000000001",
                " ",
                "team",
                "10000000-0000-4000-8000-000000000002",
                "10000000-0000-4000-8000-000000000003",
                1,
                null,
                "10000000-0000-4000-8000-000000000004",
                "invalid",
                "b".repeat(64),
                null,
                Instant.EPOCH
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static Donation donation(long amountXu, String message) {
        return new Donation(
                "10000000-0000-4000-8000-000000000001",
                "reader",
                "team",
                "10000000-0000-4000-8000-000000000002",
                "10000000-0000-4000-8000-000000000003",
                amountXu,
                message,
                "10000000-0000-4000-8000-000000000004",
                "a".repeat(64),
                "b".repeat(64),
                Donation.Status.POSTED,
                Instant.EPOCH
        );
    }
}
