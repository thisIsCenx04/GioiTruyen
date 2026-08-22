package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storyplatform.promotion.application.PrQuestService;
import com.storyplatform.promotion.application.PrQuestService.QuestDraft;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The money a PR quest moves, and the rules that decide whether it may exist.
 *
 * <p>A quest commits a team's coins to strangers before any work is done, so
 * an arithmetic slip here is not a display bug - it is coins that never come
 * back or that get paid twice.
 */
class PrQuestMoneyTest {

    private static QuestDraft draft(long reward, int slots, int days) {
        return new QuestDraft("OPEN", "TIKTOK", "PR truyện X",
                "Làm video TikTok 1000+ view, gắn link truyện", "ZALO", "0900000000",
                null, reward, slots, days);
    }

    /**
     * The worked example: 60,000 a head for five slots, plus the one flat fee.
     */
    @Test
    @DisplayName("publishing charges the budget plus one flat fee, nothing else")
    void quotesTheFullCost() {
        var cost = PrQuestService.cost(60_000L, 5);

        assertThat(cost.budgetXu()).isEqualTo(300_000L);
        assertThat(cost.publishFeeXu()).isEqualTo(10_000L);
        assertThat(cost.totalXu()).isEqualTo(310_000L);
    }

    /**
     * Không còn phần trăm nào: mọi Xu trong ngân sách hoặc đến tay người làm,
     * hoặc quay về ví nhóm. Phí duy nhất là khoản cố định lúc đăng.
     */
    @Test
    @DisplayName("the budget is never touched by a fee")
    void takesNoPercentage() {
        var small = PrQuestService.cost(1_000L, 1);
        var large = PrQuestService.cost(1_000_000L, 5);

        assertThat(small.totalXu() - small.budgetXu()).isEqualTo(PrQuestService.PUBLISH_FEE_XU);
        assertThat(large.totalXu() - large.budgetXu()).isEqualTo(PrQuestService.PUBLISH_FEE_XU);
    }

    @Test
    @DisplayName("a valid quest passes")
    void acceptsAValidQuest() {
        assertThatCode(() -> PrQuestService.validate(draft(60_000L, 5, 10)))
                .doesNotThrowAnyException();
    }

    /**
     * The floor is per person, not on the total. A 1,000 coin budget split
     * across a hundred slots is ten coins each - nobody films a video for that,
     * and the quest would only clutter the board.
     */
    @Test
    @DisplayName("a reward below the floor is refused")
    void refusesRewardBelowFloor() {
        assertThatThrownBy(() -> PrQuestService.validate(draft(10L, 100, 10)))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("pr.reward_too_low"));
    }

    @Test
    @DisplayName("a budget over the ceiling is refused, and the message names both figures")
    void refusesBudgetOverCeiling() {
        assertThatThrownBy(() -> PrQuestService.validate(draft(1_000_000L, 100, 10)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("100.000.000")
                .hasMessageContaining("10.000.000");
    }

    @Test
    @DisplayName("slot counts outside 1..100 are refused")
    void refusesSlotCountsOutOfRange() {
        assertThatThrownBy(() -> PrQuestService.validate(draft(60_000L, 0, 10)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PrQuestService.validate(draft(60_000L, 101, 10)))
                .isInstanceOf(ApiException.class);
        assertThatCode(() -> PrQuestService.validate(draft(60_000L, 1, 10)))
                .doesNotThrowAnyException();
        assertThatCode(() -> PrQuestService.validate(draft(100_000L, 100, 10)))
                .doesNotThrowAnyException();
    }

    /** A free-text duration would let a quest sit on the board for a decade. */
    @Test
    @DisplayName("only the offered durations are accepted")
    void refusesArbitraryDurations() {
        assertThatThrownBy(() -> PrQuestService.validate(draft(60_000L, 5, 30)))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("pr.duration_invalid"));
        assertThatThrownBy(() -> PrQuestService.validate(draft(60_000L, 5, 180)))
                .isInstanceOf(ApiException.class);
        assertThatCode(() -> PrQuestService.validate(draft(60_000L, 5, 5)))
                .doesNotThrowAnyException();
        assertThatCode(() -> PrQuestService.validate(draft(60_000L, 5, 10)))
                .doesNotThrowAnyException();
    }

    /**
     * The KPI is what a creator is judged against, so a quest without one is a
     * quest whose payment terms are whatever the owner later decides.
     */
    @Test
    @DisplayName("a quest with no stated KPI is refused")
    void refusesMissingRequirement() {
        var noKpi = new QuestDraft("OPEN", "TIKTOK", "PR truyện X", "   ", null, null,
                null, 60_000L, 5, 10);
        assertThatThrownBy(() -> PrQuestService.validate(noKpi))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("pr.requirement_required"));
    }

    @Test
    @DisplayName("a quest with no title is refused")
    void refusesMissingTitle() {
        var noTitle = new QuestDraft("OPEN", "TIKTOK", "", "KPI", null, null,
                null, 60_000L, 5, 10);
        assertThatThrownBy(() -> PrQuestService.validate(noTitle))
                .isInstanceOf(ApiException.class);
    }

    /**
     * A single slot is the ordinary case for a negotiated, one-creator job, so
     * the arithmetic has to hold there too.
     */
    @Test
    @DisplayName("one slot costs one reward plus the flat fee")
    void handlesTheSingleSlotCase() {
        var cost = PrQuestService.cost(1_000L, 1);
        assertThat(cost.budgetXu()).isEqualTo(1_000L);
        assertThat(cost.totalXu()).isEqualTo(11_000L);
    }
}
