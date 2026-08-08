package com.storyplatform.publishing.application;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

public final class ChapterContentSanitizer {

    static final int MAXIMUM_HTML_BYTES = 1_000_000;
    static final int MAXIMUM_NODES = 20_000;
    static final int MAXIMUM_WORDS = 30_000;

    private static final Safelist ALLOWED = new Safelist()
            .addTags(
                    "p", "br", "strong", "em", "u", "s",
                    "blockquote", "ul", "ol", "li",
                    "h2", "h3", "h4", "a", "code", "pre"
            )
            .addAttributes("a", "href", "title")
            .addProtocols("a", "href", "https");

    public SanitizedContent sanitize(String rawHtml) {
        if (rawHtml == null) {
            throw invalid("Chapter content is required.");
        }
        String normalized = Normalizer.normalize(
                rawHtml,
                Normalizer.Form.NFC
        ).trim();
        if (normalized.isBlank()
                || normalized.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_HTML_BYTES) {
            throw invalid("Chapter content exceeds its byte limit.");
        }
        Document dirty = Jsoup.parseBodyFragment(normalized);
        if (dirty.getAllElements().size() > MAXIMUM_NODES) {
            throw invalid("Chapter content exceeds its node limit.");
        }
        Document clean = new Cleaner(ALLOWED).clean(dirty);
        clean.outputSettings().prettyPrint(false);
        String html = clean.body().html().trim();
        String text = clean.body().text().trim();
        if (text.isBlank()) {
            throw invalid("Chapter content has no readable text.");
        }
        long words = java.util.Arrays.stream(text.split("\\s+"))
                .filter(value -> !value.isBlank())
                .limit(MAXIMUM_WORDS + 1L)
                .count();
        if (words > MAXIMUM_WORDS) {
            throw invalid("Chapter content exceeds its word limit.");
        }
        return new SanitizedContent(html, text, (int) words);
    }

    public record SanitizedContent(
            String html,
            String plainText,
            int wordCount
    ) {
    }

    private static ChapterDraftException invalid(String message) {
        return new ChapterDraftException(
                "CHAPTER_CONTENT_INVALID",
                message,
                ChapterDraftException.Kind.INVALID
        );
    }
}
