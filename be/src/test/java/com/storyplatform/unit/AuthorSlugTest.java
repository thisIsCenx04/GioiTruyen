package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyplatform.author.application.AuthorApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Team names are Vietnamese, and the slug becomes a public URL, so tone marks
 * and đ have to survive the trip as plain ASCII.
 */
class AuthorSlugTest {

    @Test
    @DisplayName("strips tone marks and lower-cases")
    void stripsToneMarks() {
        assertThat(AuthorApplicationService.slugify("Nhà Dịch Ánh Trăng"))
                .isEqualTo("nha-dich-anh-trang");
    }

    @Test
    @DisplayName("turns đ into d rather than dropping it")
    void handlesDWithStroke() {
        assertThat(AuthorApplicationService.slugify("Đường Về")).isEqualTo("duong-ve");
    }

    @Test
    @DisplayName("collapses punctuation and trims the edges")
    void collapsesPunctuation() {
        assertThat(AuthorApplicationService.slugify("  Team --- Số 1!  ")).isEqualTo("team-so-1");
    }

    @Test
    @DisplayName("a name with nothing slug-able comes back empty, not as dashes")
    void emptyWhenNothingRemains() {
        assertThat(AuthorApplicationService.slugify("!!!")).isEmpty();
    }
}
