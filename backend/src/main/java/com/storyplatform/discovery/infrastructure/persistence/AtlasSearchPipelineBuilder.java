package com.storyplatform.discovery.infrastructure.persistence;

import com.storyplatform.discovery.application.port.StorySearchRepository;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AtlasSearchPipelineBuilder {

    private static final List<String> TEXT_PATHS = List.of(
            "title",
            "aliases",
            "synopsis"
    );

    private final String indexName;

    public AtlasSearchPipelineBuilder(String indexName) {
        if (indexName == null
                || !indexName.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException(
                    "Atlas Search index name is invalid"
            );
        }
        this.indexName = indexName;
    }

    public List<Document> results(
            StorySearchRepository.SearchQuery query
    ) {
        Objects.requireNonNull(query, "query");
        Document search = new Document("index", indexName)
                .append("compound", compound(query))
                .append(
                        "highlight",
                        new Document("path", List.of("title", "aliases"))
                )
                .append(
                        "sort",
                        new Document(
                                "score",
                                new Document("$meta", "searchScore")
                        ).append("_id", 1)
                );
        if (query.atlasCursor() != null) {
            search.append("searchAfter", query.atlasCursor());
        }
        return List.of(
                new Document("$search", search),
                new Document("$limit", query.limit() + 1),
                new Document("$project", new Document("_id", 1)
                        .append("teamId", 1)
                        .append("slug", 1)
                        .append("title", 1)
                        .append("coverAssetId", 1)
                        .append("publishedAt", 1)
                        .append(
                                "score",
                                new Document("$meta", "searchScore")
                        )
                        .append(
                                "paginationToken",
                                new Document(
                                        "$meta",
                                        "searchSequenceToken"
                                )
                        )
                        .append(
                                "highlights",
                                new Document("$meta", "searchHighlights")
                        ))
        );
    }

    public List<Document> facets(
            StorySearchRepository.SearchQuery query
    ) {
        Document facet = new Document("operator", compound(query))
                .append("facets", new Document(
                        "categoryIds",
                        stringFacet("categoryIds", 20)
                ).append(
                        "completionStatus",
                        stringFacet("completionStatus", 10)
                ).append("origin", stringFacet("origin", 10)));
        return List.of(new Document(
                "$searchMeta",
                new Document("index", indexName).append("facet", facet)
        ));
    }

    private static Document compound(
            StorySearchRepository.SearchQuery query
    ) {
        List<Document> filters = new ArrayList<>();
        filters.add(equalsFilter("workflowStatus", "PUBLISHED"));
        if (query.categoryId() != null) {
            filters.add(equalsFilter("categoryIds", query.categoryId()));
        }
        if (query.completionStatus() != null) {
            filters.add(equalsFilter(
                    "completionStatus",
                    query.completionStatus()
            ));
        }
        if (query.origin() != null) {
            filters.add(equalsFilter("origin", query.origin()));
        }
        return new Document("must", List.of(new Document(
                "text",
                new Document("query", query.text())
                        .append("path", TEXT_PATHS)
                        .append("fuzzy", new Document("maxEdits", 1)
                                .append("prefixLength", 2))
        ))).append("filter", filters);
    }

    private static Document equalsFilter(String path, String value) {
        return new Document(
                "equals",
                new Document("path", path).append("value", value)
        );
    }

    private static Document stringFacet(String path, int buckets) {
        return new Document("type", "string")
                .append("path", path)
                .append("numBuckets", buckets);
    }
}
