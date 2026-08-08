package com.storyplatform.unit.publishing.application;

import com.storyplatform.publishing.application.ChapterContentSanitizer;
import com.storyplatform.publishing.application.ChapterDraftException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterContentSanitizerTest {

    private final ChapterContentSanitizer sanitizer =
            new ChapterContentSanitizer();

    @Test
    void preservesSafeMarkupAndRemovesExecutableContent() {
        var result = sanitizer.sanitize("""
                <p onclick="steal()">Hello <strong>world</strong></p>
                <script>alert(1)</script><iframe src="https://evil.test"></iframe>
                <a href="javascript:alert(2)">bad</a>
                <a href="https://example.test" title="safe">good</a>
                """);

        assertThat(result.html())
                .contains("<strong>world</strong>")
                .contains("https://example.test")
                .doesNotContain(
                        "onclick",
                        "script",
                        "iframe",
                        "javascript:"
                );
        assertThat(result.plainText()).contains("Hello world", "good");
        assertThat(result.wordCount()).isEqualTo(4);
    }

    @Test
    void rejectsBlankAndOversizedUtf8Content() {
        assertThatThrownBy(() -> sanitizer.sanitize(null))
                .isInstanceOf(ChapterDraftException.class);
        assertThatThrownBy(() -> sanitizer.sanitize("  "))
                .isInstanceOf(ChapterDraftException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(
                "á".repeat(500_001)
        )).isInstanceOf(ChapterDraftException.class)
                .extracting("code")
                .isEqualTo("CHAPTER_CONTENT_INVALID");
    }

    @Test
    void rejectsMarkupWithoutReadableText() {
        assertThatThrownBy(() -> sanitizer.sanitize(
                "<script>alert(1)</script><br>"
        )).isInstanceOf(ChapterDraftException.class)
                .extracting("code")
                .isEqualTo("CHAPTER_CONTENT_INVALID");
    }

    @Test
    void rejectsExcessiveWordCount() {
        assertThatThrownBy(() -> sanitizer.sanitize(
                "word ".repeat(30_001)
        )).isInstanceOf(ChapterDraftException.class)
                .extracting("code")
                .isEqualTo("CHAPTER_CONTENT_INVALID");
    }
}
