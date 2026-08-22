package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storyplatform.admin.api.AdminStoryController;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Submitting chapters replaces every chapter a story has, so a short list is
 * destructive. These cover the boundary that decides whether it is applied.
 */
class ChapterLossGuardTest {

    @Test
    @DisplayName("a shorter list is refused rather than silently deleting chapters")
    void refusesShorterList() {
        assertThatThrownBy(() -> AdminStoryController.checkNoChapterLoss(1200, 800, false))
                .isInstanceOfSatisfying(ApiException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(failure.code()).isEqualTo("story.chapters_would_be_lost");
                });
    }

    @Test
    @DisplayName("the refusal names both counts so the admin can tell what happened")
    void explainsTheCounts() {
        assertThatThrownBy(() -> AdminStoryController.checkNoChapterLoss(1200, 800, false))
                .hasMessageContaining("1200")
                .hasMessageContaining("800");
    }

    @Test
    @DisplayName("replacing with the same count or more is allowed")
    void allowsSameOrLonger() {
        assertThatCode(() -> AdminStoryController.checkNoChapterLoss(800, 800, false)).doesNotThrowAnyException();
        assertThatCode(() -> AdminStoryController.checkNoChapterLoss(800, 801, false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a story with no chapters yet accepts its first upload")
    void allowsFirstUpload() {
        assertThatCode(() -> AdminStoryController.checkNoChapterLoss(0, 1, false)).doesNotThrowAnyException();
    }

    /**
     * The form has a delete button on every chapter. With the guard applied to
     * every short list, pressing it and saving was refused - and the refusal
     * advised deleting chapters one at a time, which is exactly what had just
     * been blocked. A confirmed deletion has to get through.
     */
    @Test
    @DisplayName("a confirmed deletion is allowed through")
    void allowsConfirmedDeletion() {
        assertThatCode(() -> AdminStoryController.checkNoChapterLoss(1200, 800, true))
                .doesNotThrowAnyException();
        assertThatCode(() -> AdminStoryController.checkNoChapterLoss(1, 0, true))
                .doesNotThrowAnyException();
    }
}
