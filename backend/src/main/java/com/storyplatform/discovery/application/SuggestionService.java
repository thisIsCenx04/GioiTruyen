package com.storyplatform.discovery.application;

import com.storyplatform.discovery.application.port.SearchCursorCodec;
import com.storyplatform.discovery.application.port.SuggestionRateLimiter;
import com.storyplatform.discovery.application.port.SuggestionRepository;

import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SuggestionService implements SuggestionOperations {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final SuggestionRepository repository;
    private final SearchCursorCodec cursors;
    private final SuggestionRateLimiter limiter;

    public SuggestionService(
            SuggestionRepository repository,
            SearchCursorCodec cursors,
            SuggestionRateLimiter limiter
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
    }

    @Override
    public SuggestionResponse suggest(SuggestionRequest request) {
        Objects.requireNonNull(request, "request");
        if (!limiter.allow(request.rateLimitSubject())) {
            throw new SuggestionRateLimitException(
                    limiter.retryAfterSeconds()
            );
        }
        String query = normalize(request.query());
        if (request.limit() < 1 || request.limit() > 10) {
            throw new IllegalArgumentException(
                    "suggestion limit must be from 1 to 10"
            );
        }
        String atlasCursor = null;
        if (request.cursor() != null && !request.cursor().isBlank()) {
            try {
                atlasCursor = cursors.decode(request.cursor());
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException(
                        "suggestion cursor is invalid",
                        exception
                );
            }
        }
        SuggestionRepository.SuggestionPage result = repository.find(
                query,
                atlasCursor,
                request.limit()
        );
        return new SuggestionResponse(
                result.items(),
                result.nextAtlasCursor() == null
                        ? null
                        : cursors.encode(result.nextAtlasCursor()),
                result.hasMore()
        );
    }

    private static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    "suggestion query is required"
            );
        }
        String value = WHITESPACE.matcher(Normalizer.normalize(
                raw,
                Normalizer.Form.NFKC
        ).trim()).replaceAll(" ");
        if (value.length() < 2 || value.length() > 80
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "suggestion query must contain 2 to 80 safe characters"
            );
        }
        return value;
    }
}
