package com.storyplatform.discovery.infrastructure.persistence;

import com.mongodb.client.MongoCollection;
import com.storyplatform.discovery.application.SuggestionOperations;
import com.storyplatform.discovery.application.port.SuggestionRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class AtlasSuggestionRepository
        implements SuggestionRepository {

    private static final long TIMEOUT_MILLIS = 300;

    private final MongoCollection<Document> stories;
    private final String indexName;

    public AtlasSuggestionRepository(
            MongoTemplate mongo,
            String indexName
    ) {
        Objects.requireNonNull(mongo, "mongo");
        if (indexName == null
                || !indexName.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException(
                    "Atlas Search index name is invalid"
            );
        }
        stories = mongo.getCollection("stories");
        this.indexName = indexName;
    }

    @Override
    public SuggestionPage find(
            String prefix,
            String atlasCursor,
            int limit
    ) {
        List<Document> loaded = stories.aggregate(pipeline(
                        prefix,
                        atlasCursor,
                        limit + 1
                ))
                .maxTime(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .into(new ArrayList<>());
        boolean hasMore = loaded.size() > limit;
        List<Document> page = hasMore
                ? loaded.subList(0, limit)
                : loaded;
        List<SuggestionOperations.Suggestion> items = page.stream()
                .map(value -> new SuggestionOperations.Suggestion(
                        value.getString("_id"),
                        value.getString("slug"),
                        value.getString("title"),
                        value.getString("coverAssetId")
                ))
                .toList();
        return new SuggestionPage(
                items,
                hasMore
                        ? page.getLast().getString("paginationToken")
                        : null,
                hasMore
        );
    }

    List<Document> pipeline(
            String prefix,
            String atlasCursor,
            int limit
    ) {
        Document search = new Document("index", indexName)
                .append("compound", new Document(
                        "must",
                        List.of(new Document(
                                "autocomplete",
                                new Document("query", prefix)
                                        .append("path", "title")
                                        .append("tokenOrder", "sequential")
                                        .append("fuzzy", new Document(
                                                "maxEdits",
                                                1
                                        ).append("prefixLength", 2))
                        ))
                ).append("filter", List.of(new Document(
                        "equals",
                        new Document("path", "workflowStatus")
                                .append("value", "PUBLISHED")
                ))))
                .append("sort", new Document(
                        "score",
                        new Document("$meta", "searchScore")
                ).append("_id", 1));
        if (atlasCursor != null) {
            search.append("searchAfter", atlasCursor);
        }
        return List.of(
                new Document("$search", search),
                new Document("$limit", limit),
                new Document("$project", new Document("_id", 1)
                        .append("slug", 1)
                        .append("title", 1)
                        .append("coverAssetId", 1)
                        .append(
                                "paginationToken",
                                new Document(
                                        "$meta",
                                        "searchSequenceToken"
                                )
                        ))
        );
    }
}
