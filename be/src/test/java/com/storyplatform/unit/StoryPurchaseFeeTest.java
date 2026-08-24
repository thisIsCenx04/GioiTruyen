package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyplatform.monetization.application.MonetizationFlowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Phí nền tảng trên mỗi lượt mua truyện.
 *
 * <p>Quy định: truyện độc quyền giữ 10%, truyện thường giữ 30%. Trước đây mọi
 * truyện đều ăn chung một mức 20%, nên một combo 90.000 xu trả về 72.000 cho cả
 * hai loại — thừa với truyện thường và thiếu với truyện độc quyền.
 */
class StoryPurchaseFeeTest {

    /** Đúng con số trong ảnh chụp: combo 90.000 xu. */
    private static final long COMBO = 90_000L;

    @Test
    @DisplayName("truyện độc quyền: phí 10%, nhóm nhận 81.000 trên combo 90.000")
    void exclusiveKeepsNinetyPercent() {
        assertThat(MonetizationFlowService.teamNetFor(COMBO, "EXCLUSIVE")).isEqualTo(81_000L);
    }

    @Test
    @DisplayName("truyện thường: phí 30%, nhóm nhận 63.000 trên combo 90.000")
    void standardKeepsSeventyPercent() {
        assertThat(MonetizationFlowService.teamNetFor(COMBO, "TEXT")).isEqualTo(63_000L);
        assertThat(MonetizationFlowService.teamNetFor(COMBO, "AUDIO")).isEqualTo(63_000L);
    }

    /**
     * Truyện sáng tác là của chính người đăng nên mặc nhiên là độc quyền, hưởng
     * cùng mức 10%.
     */
    @Test
    @DisplayName("truyện sáng tác hưởng mức độc quyền")
    void originalCountsAsExclusive() {
        assertThat(MonetizationFlowService.teamNetFor(COMBO, "ORIGINAL")).isEqualTo(81_000L);
    }

    /** Loại truyện thiếu hoặc lạ thì tính mức thường — không cho hưởng nhầm 10%. */
    @Test
    @DisplayName("loại truyện không đọc được thì tính mức thường")
    void unknownTypeFallsBackToStandard() {
        assertThat(MonetizationFlowService.teamNetFor(COMBO, null)).isEqualTo(63_000L);
        assertThat(MonetizationFlowService.teamNetFor(COMBO, "exclusive")).isEqualTo(63_000L);
    }

    /** Phí làm tròn xuống, nên phần lẻ rơi về nhóm chứ không về nền tảng. */
    @Test
    @DisplayName("phần lẻ khi chia không tròn thuộc về nhóm")
    void remainderGoesToTheTeam() {
        // 999 * 0.30 = 299,7 -> phí 299, nhóm nhận 700.
        assertThat(MonetizationFlowService.teamNetFor(999L, "TEXT")).isEqualTo(700L);
        // 999 * 0.10 = 99,9 -> phí 99, nhóm nhận 900.
        assertThat(MonetizationFlowService.teamNetFor(999L, "EXCLUSIVE")).isEqualTo(900L);
    }

    @Test
    @DisplayName("mức phí theo quy định là 10% và 30%")
    void ratesMatchThePolicy() {
        assertThat(MonetizationFlowService.purchaseFeeRateFor("EXCLUSIVE")).isEqualByComparingTo("0.10");
        assertThat(MonetizationFlowService.purchaseFeeRateFor("TEXT")).isEqualByComparingTo("0.30");
    }
}
