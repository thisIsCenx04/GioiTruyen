package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.RewardRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RewardRuleTest {

    private final RewardRule rule = new RewardRule(
            "reward-2026.1",
            100,
            1_000
    );

    @Test
    void floorsFractionalXuAndCapsDeterministically() {
        assertThat(rule.calculate(9).amountXu()).isZero();
        assertThat(rule.calculate(10).amountXu()).isOne();
        assertThat(rule.calculate(9_999).amountXu()).isEqualTo(999);
        assertThat(rule.calculate(10_000).amountXu()).isEqualTo(1_000);
        assertThat(rule.calculate(Long.MAX_VALUE).amountXu())
                .isEqualTo(1_000);
        assertThat(rule.calculate(Long.MAX_VALUE).capApplied()).isTrue();
    }

    @Test
    void rejectsInvalidRulesAndNegativeViewCounts() {
        assertThatThrownBy(() -> new RewardRule("bad version", 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rule.calculate(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
