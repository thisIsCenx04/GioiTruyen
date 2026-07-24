package com.storyplatform.community.application;

import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

public final class CommentSanitizer {

    public static final int MAXIMUM_CHARACTERS = 5_000;
    private static final int MAXIMUM_BYTES = 20_000;
    private static final int MAXIMUM_LINKS = 5;

    public String sanitize(String raw) {
        if (raw == null) {
            throw invalid("Comment body is required.");
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
                .replace("\u0000", "")
                .strip();
        if (normalized.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_BYTES) {
            throw invalid("Comment body is too large.");
        }
        org.jsoup.nodes.Document document =
                Jsoup.parseBodyFragment(normalized);
        document.select(
                "script,style,noscript,iframe,object,embed,template"
        ).remove();
        String plain = document.body().text()
                .replaceAll("\\s+", " ")
                .strip();
        if (plain.isBlank() || plain.codePointCount(0, plain.length())
                > MAXIMUM_CHARACTERS) {
            throw invalid("Comment body must contain 1 to 5000 characters.");
        }
        String lower = plain.toLowerCase(Locale.ROOT);
        int links = occurrences(lower, "http://")
                + occurrences(lower, "https://")
                + occurrences(lower, "www.");
        if (links > MAXIMUM_LINKS) {
            throw invalid("Comment contains too many links.");
        }
        return plain;
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static CommentException invalid(String message) {
        return new CommentException(
                "COMMENT_BODY_INVALID",
                message,
                CommentException.Kind.INVALID
        );
    }
}
