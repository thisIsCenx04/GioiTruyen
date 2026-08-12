package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storyplatform.monetization.application.TopupService;
import com.storyplatform.shared.api.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Only a reader confirming a transfer counts against the limit; opening the
 * payment screen does not reach this check at all.
 */
class TopupSubmissionRateTest {

    @Test
    @DisplayName("the first three submissions in a window are allowed")
    void allowsUpToTheLimit() {
        assertThatCode(() -> TopupService.checkSubmissionRate(0)).doesNotThrowAnyException();
        assertThatCode(() -> TopupService.checkSubmissionRate(2)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the fourth is refused with 429 and a retry hint")
    void refusesBeyondTheLimit() {
        assertThatThrownBy(() -> TopupService.checkSubmissionRate(3))
                .isInstanceOfSatisfying(ApiException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(failure.code()).isEqualTo("topup.too_many_submissions");
                    assertThat(failure.retryAfter()).isEqualTo(Duration.ofMinutes(1));
                });
    }

    @Test
    @DisplayName("the refusal tells the reader what to do rather than only that it failed")
    void explainsWhatToDo() {
        assertThatThrownBy(() -> TopupService.checkSubmissionRate(9))
                .hasMessageContaining("đợi")
                .hasMessageContaining("hỗ trợ");
    }
}
