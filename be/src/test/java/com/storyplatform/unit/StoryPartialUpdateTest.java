package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyplatform.admin.api.AdminStoryController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lưu truyện là một form duy nhất dùng cho nhiều việc: sửa chương, đổi thể
 * loại, đặt giá combo. Trước đây mọi lần lưu đều ghi đè cả giới thiệu lẫn giá
 * combo, nên sửa một thứ xoá mất thứ khác. Hai chốt chặn dưới đây là chỗ quyết
 * định trường nào thực sự được ghi.
 */
class StoryPartialUpdateTest {

    /** Đúng dấu vết tìm thấy trong CSDL: teaser 499 ký tự đè lên văn án dài. */
    private static final String TEASER = "x".repeat(499);
    private static final String FULL_SYNOPSIS = "x".repeat(499) + " và phần còn lại của văn án";

    @Test
    @DisplayName("teaser đang lưu ghi đè lên văn án dài hơn thì bị chặn")
    void refusesTeaserOverwritingFullText() {
        assertThat(AdminStoryController.isTeaserOverwrite(FULL_SYNOPSIS, TEASER, TEASER)).isTrue();
    }

    @Test
    @DisplayName("người dùng thật sự rút ngắn văn án thì vẫn lưu được")
    void allowsGenuineShortening() {
        assertThat(AdminStoryController.isTeaserOverwrite(FULL_SYNOPSIS, TEASER, "Văn án viết lại, ngắn hơn"))
                .isFalse();
    }

    @Test
    @DisplayName("văn án dài hơn bản cũ luôn được lưu")
    void allowsLongerText() {
        assertThat(AdminStoryController.isTeaserOverwrite(TEASER, TEASER, FULL_SYNOPSIS)).isFalse();
    }

    @Test
    @DisplayName("truyện chưa có giới thiệu thì không có gì để bảo vệ")
    void allowsFirstSynopsis() {
        assertThat(AdminStoryController.isTeaserOverwrite(null, null, TEASER)).isFalse();
    }

    /**
     * Phân biệt "không gửi" với "gửi số 0" là điều kiện để sửa chương không xoá
     * mất combo: null giữ nguyên giá đang có, 0 mới là cố ý gỡ combo.
     */
    @Test
    @DisplayName("ô combo bỏ trống nghĩa là không đụng tới giá đang có")
    void blankComboMeansUntouched() {
        assertThat(AdminStoryController.parseCoin(null)).isNull();
        assertThat(AdminStoryController.parseCoin("")).isNull();
        assertThat(AdminStoryController.parseCoin("   ")).isNull();
    }

    @Test
    @DisplayName("số 0 gửi lên là cố ý gỡ combo, khác hẳn với không gửi gì")
    void zeroMeansRemoveCombo() {
        assertThat(AdminStoryController.parseCoin("0")).isEqualTo(0L);
        assertThat(AdminStoryController.parseCoin("-5")).isEqualTo(0L);
    }

    @Test
    @DisplayName("số dương là giá combo cần đặt")
    void positiveMeansPrice() {
        assertThat(AdminStoryController.parseCoin(" 1200 ")).isEqualTo(1200L);
    }

    /** Một ô nhập hỏng không được hiểu thành lệnh gỡ combo. */
    @Test
    @DisplayName("giá trị không phải số thì coi như không gửi")
    void nonNumericMeansUntouched() {
        assertThat(AdminStoryController.parseCoin("abc")).isNull();
    }
}
