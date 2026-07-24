package com.storyplatform.unit.monetization.domain;

import com.storyplatform.monetization.application.ReferralRule;
import com.storyplatform.monetization.domain.ReferralAttribution;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferralAttributionTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");

    @Test
    void pendingAttributionTransitionsOnceAfterEligibility() {
        ReferralAttribution pending = pending();
        ReferralAttribution rewarded = pending.rewarded(
                "40000000-0000-4000-8000-000000000001",
                NOW.plusSeconds(60)
        );

        assertThat(rewarded.state())
                .isEqualTo(ReferralAttribution.State.REWARDED);
        assertThatThrownBy(() -> rewarded.rewarded(
                "50000000-0000-4000-8000-000000000001",
                NOW.plusSeconds(120)
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.rewarded(
                "40000000-0000-4000-8000-000000000001",
                NOW.minusSeconds(1)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInconsistentIdentityHashAndRewardState() {
        assertThatThrownBy(() -> new ReferralAttribution(
                "invalid",
                "referrer",
                "referrer",
                "bad",
                "BAD",
                0,
                ReferralAttribution.State.PENDING,
                List.of(),
                null,
                NOW,
                NOW.minusSeconds(1),
                null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReferralAttribution(
                "10000000-0000-4000-8000-000000000001",
                "referrer",
                "referee",
                "a".repeat(64),
                "referral-2026.1",
                100,
                ReferralAttribution.State.REWARDED,
                List.of(),
                null,
                NOW,
                NOW,
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ruleRequiresPositiveBoundedVersionedControls() {
        assertThat(new ReferralRule(
                "referral-2026.1",
                100,
                Duration.ofDays(7),
                Duration.ofDays(7),
                Duration.ofDays(7),
                100
        ).maximumRecentReferrals()).isEqualTo(100);
        assertInvalidRule(
                "BAD", 100, Duration.ofDays(7),
                Duration.ofDays(7), Duration.ofDays(7), 100
        );
        assertInvalidRule(
                "referral-2026.1", 0, Duration.ofDays(7),
                Duration.ofDays(7), Duration.ofDays(7), 100
        );
        assertInvalidRule(
                "referral-2026.1", 100, null,
                Duration.ofDays(7), Duration.ofDays(7), 100
        );
        assertInvalidRule(
                "referral-2026.1", 100, Duration.ofDays(7),
                Duration.ZERO, Duration.ofDays(7), 100
        );
        assertInvalidRule(
                "referral-2026.1", 100, Duration.ofDays(7),
                Duration.ofDays(7), Duration.ofSeconds(-1), 100
        );
        assertInvalidRule(
                "referral-2026.1", 100, Duration.ofDays(7),
                Duration.ofDays(7), Duration.ofDays(7), 10_001
        );
    }

    private static ReferralAttribution pending() {
        return new ReferralAttribution(
                "10000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                "a".repeat(64),
                "referral-2026.1",
                100,
                ReferralAttribution.State.PENDING,
                List.of(),
                null,
                NOW.minusSeconds(60),
                NOW,
                null
        );
    }

    private static void assertInvalidRule(
            String version,
            long reward,
            Duration window,
            Duration minimumAge,
            Duration delay,
            int maximum
    ) {
        assertThatThrownBy(() -> new ReferralRule(
                version,
                reward,
                window,
                minimumAge,
                delay,
                maximum
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
