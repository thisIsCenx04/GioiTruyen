package com.storyplatform.discovery.application;

import com.storyplatform.discovery.application.port.SearchCursorCodec;
import com.storyplatform.discovery.application.port.StorySearchRepository;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class StorySearchService implements SearchOperations {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern STATUS = Pattern.compile(
            "ONGOING|COMPLETED|HIATUS"
    );
    private static final Pattern ORIGIN = Pattern.compile(
            "ORIGINAL|TRANSLATED"
    );

    private final StorySearchRepository repository;
    private final SearchCursorCodec cursors;

    public StorySearchService(
            StorySearchRepository repository,
            SearchCursorCodec cursors
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        Objects.requireNonNull(request, "request");
        String query = normalizeQuery(request.query());
        if (request.limit() < 1 || request.limit() > 50) {
            throw invalid("limit must be from 1 to 50");
        }
        String categoryId = uuid(request.categoryId());
        String status = enumValue(request.completionStatus(), STATUS);
        String origin = enumValue(request.origin(), ORIGIN);
        String atlasCursor = null;
        if (request.cursor() != null && !request.cursor().isBlank()) {
            try {
                atlasCursor = cursors.decode(request.cursor());
            } catch (RuntimeException exception) {
                throw invalid("cursor is invalid");
            }
        }
        long started = System.nanoTime();
        StorySearchRepository.SearchPage result = repository.search(
                new StorySearchRepository.SearchQuery(
                        query,
                        categoryId,
                        status,
                        origin,
                        atlasCursor,
                        request.limit()
                )
        );
        String next = result.nextAtlasCursor() == null
                ? null
                : cursors.encode(result.nextAtlasCursor());
        long tookMs = (System.nanoTime() - started) / 1_000_000;
        return new SearchResponse(
                result.items(),
                next,
                result.hasMore(),
                result.facets(),
                tookMs
        );
    }

    private static String normalizeQuery(String raw) {
        if (raw == null) {
            throw invalid("query is required");
        }
        String normalized = WHITESPACE.matcher(Normalizer.normalize(
                raw,
                Normalizer.Form.NFKC
        ).trim()).replaceAll(" ");
        if (normalized.length() < 2 || normalized.length() > 200
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid("query must contain 2 to 200 safe characters");
        }
        return normalized;
    }

    private static String uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw invalid("categoryId must be a UUID");
        }
    }

    private static String enumValue(String value, Pattern allowlist) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!allowlist.matcher(normalized).matches()) {
            throw invalid("filter value is not supported");
        }
        return normalized;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
