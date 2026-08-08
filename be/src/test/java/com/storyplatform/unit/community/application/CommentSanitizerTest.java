package com.storyplatform.unit.community.application;

import com.storyplatform.community.application.CommentException;
import com.storyplatform.community.application.CommentSanitizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentSanitizerTest {

    private final CommentSanitizer sanitizer = new CommentSanitizer();

    @Test
    void stripsExecutableMarkupAndNormalizesWhitespace() {
        String safe = sanitizer.sanitize(
                "  Xin <strong>chào</strong>"
                        + "<img src=x onerror=alert(1)>   bạn  "
        );

        assertThat(safe).isEqualTo("Xin chào bạn");
        assertThat(safe).doesNotContain("<", "onerror", "alert");
    }

    @Test
    void rejectsMarkupWithoutReadableTextAndLinkSpam() {
        assertThatThrownBy(() -> sanitizer.sanitize(
                "<script>alert(1)</script>"
        )).isInstanceOf(CommentException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(
                "https://a.test https://b.test https://c.test "
                        + "https://d.test https://e.test https://f.test"
        )).isInstanceOf(CommentException.class);
    }
}
