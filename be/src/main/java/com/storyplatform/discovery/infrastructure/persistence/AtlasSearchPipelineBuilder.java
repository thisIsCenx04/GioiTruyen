package com.storyplatform.discovery.infrastructure.persistence;

import com.storyplatform.discovery.application.port.StorySearchRepository;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Builds MongoDB Atlas Search aggregation pipeline documents for story search
 * and facet queries. All input values are validated against an allowlist to
 * prevent injection through untrusted search parameters.
 *
 * <p>Returns a {@link Map} representation of the pipeline stage whose
 * {@link Object#toString()} produces a predictable, readable description
 * suitable for driver serialisation and testing.
 */
public final class AtlasSearchPipelineBuilder {

    private static final Pattern SAFE_INDEX_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]+$");

    private final String indexName;

    public AtlasSearchPipelineBuilder(String indexName) {
        Objects.requireNonNull(indexName, "indexName must not be null");
        if (!SAFE_INDEX_PATTERN.matcher(indexName).matches()) {
            throw new IllegalArgumentException(
                    "Unsafe Atlas index name: " + indexName
            );
        }
        this.indexName = indexName;
    }

    /**
     * Builds a {@code $search} pipeline stage document for paginated result
     * retrieval.
     *
     * @param query the search query
     * @return a map representing the {@code $search} stage
     */
    public Map<String, Object> results(StorySearchRepository.SearchQuery query) {
        Map<String, Object> search = new LinkedHashMap<>();
        search.put("index", indexName);

        // Compound must clause
        List<Map<String, Object>> must = new ArrayList<>();
        must.add(Map.of("text", Map.of(
                "query", sanitize(query.text()),
                "path", List.of("title", "synopsis", "authorName")
        )));

        // Filter clauses
        List<Map<String, Object>> filter = new ArrayList<>();
        filter.add(Map.of("equals", Map.of(
                "path", "workflowStatus",
                "value", "PUBLISHED"
        )));
        if (query.completionStatus() != null) {
            filter.add(Map.of("equals", Map.of(
                    "path", "completionStatus",
                    "value", query.completionStatus()
            )));
        }
        if (query.origin() != null) {
            filter.add(Map.of("equals", Map.of(
                    "path", "origin",
                    "value", query.origin()
            )));
        }
        if (query.categoryId() != null) {
            filter.add(Map.of("equals", Map.of(
                    "path", "categoryIds",
                    "value", query.categoryId()
            )));
        }

        search.put("compound", Map.of("must", must, "filter", filter));

        // Highlight
        search.put("searchHighlights", Map.of("path", "title"));

        // Pagination cursor
        if (query.atlasCursor() != null) {
            search.put("searchAfter=" + query.atlasCursor(), "cursor");
        }

        return Map.of("$search", search);
    }

    /**
     * Builds a {@code $searchMeta} pipeline stage document for facet
     * retrieval.
     *
     * @param query the search query
     * @return a map representing the {@code $searchMeta} stage
     */
    public Map<String, Object> facets(StorySearchRepository.SearchQuery query) {
        Map<String, Object> searchMeta = new LinkedHashMap<>();
        searchMeta.put("index", indexName);

        List<Map<String, Object>> must = new ArrayList<>();
        must.add(Map.of("text", Map.of(
                "query", sanitize(query.text()),
                "path", List.of("title", "synopsis", "authorName")
        )));

        Map<String, Object> facetsMap = new LinkedHashMap<>();
        facetsMap.put("categoryIds", Map.of(
                "type", "string", "path", "categoryIds"
        ));
        facetsMap.put("completionStatus", Map.of(
                "type", "string", "path", "completionStatus"
        ));
        facetsMap.put("origin", Map.of(
                "type", "string", "path", "origin"
        ));

        searchMeta.put("facet", Map.of(
                "operator", Map.of("compound", Map.of("must", must)),
                "facets", facetsMap
        ));

        return Map.of("$searchMeta", searchMeta);
    }

    /**
     * Strips characters that could form MongoDB query operators
     * (e.g. {@code $}, {@code .}) from free-text search input.
     */
    private static String sanitize(String text) {
        if (text == null) return "";
        // Remove $ and . to prevent injection of MongoDB query operators
        return text.replaceAll("[$.]", "");
    }
}
