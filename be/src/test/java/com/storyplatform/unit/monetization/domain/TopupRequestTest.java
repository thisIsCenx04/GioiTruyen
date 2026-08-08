package com.storyplatform.unit.monetization.domain;

import com.storyplatform.monetization.domain.TopupRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopupRequestTest {

    @Test
    void calculatesFloorWithVersionedPercentageBoundaries() {
        assertThat(TopupRequest.calculateCreditedXu(
                100_000,
                BigDecimal.TEN
        )).isEqualTo(90_000);
        assertThat(TopupRequest.calculateCreditedXu(
                10_001,
                new BigDecimal("12.50")
        )).isEqualTo(8_750);
        assertThat(TopupRequest.calculateCreditedXu(
                1_000_000_000,
                BigDecimal.ZERO
        )).isEqualTo(1_000_000_000);
        assertThat(TopupRequest.calculateCreditedXu(
                10_000,
                new BigDecimal("100")
        )).isZero();
    }

    @Test
    void rejectsAmountsAndSnapshotsOutsideTheContract() {
        assertThatThrownBy(() -> TopupRequest.calculateCreditedXu(
                9_999,
                BigDecimal.TEN
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TopupRequest.calculateCreditedXu(
                1_000_000_001,
                BigDecimal.TEN
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TopupRequest.calculateCreditedXu(
                100_000,
                new BigDecimal("10.001")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTamperedCreditedValueAndExpiry() {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        assertThatThrownBy(() -> new TopupRequest(
                "10000000-0000-4000-8000-000000000001",
                "reader",
                100_000,
                90_001,
                BigDecimal.TEN,
                1,
                "GT12345678901234",
                "qr-payload",
                TopupRequest.Status.AWAITING_PAYMENT,
                now.plusSeconds(1),
                now,
                "a".repeat(64),
                "b".repeat(64)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }
}
