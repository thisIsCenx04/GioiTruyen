package com.storyplatform.discovery.infrastructure.persistence;

import com.mongodb.client.MongoCollection;
import com.storyplatform.discovery.application.HomeStorySummary;
import com.storyplatform.discovery.application.SearchOperations;
import com.storyplatform.discovery.application.port.StorySearchRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class AtlasStorySearchRepository
        implements StorySearchRepository {

    private static final String STORY_COLLECTION = "stories";
    private static final long TIMEOUT_MILLIS = 450;

    private final MongoCollection<Document> stories;
    private final AtlasSearchPipelineBuilder pipelines;

    public AtlasStorySearchRepository(
            MongoTemplate mongo,
            String indexName
    ) {
        Objects.requireNonNull(mongo, "mongo");
        stories = mongo.getCollection(STORY_COLLECTION);
        pipelines = new AtlasSearchPipelineBuilder(indexName);
    }

    @Override
    public SearchPage search(SearchQuery query) {
        List<Document> loaded = stories.aggregate(
                        pipelines.results(query))
                .maxTime(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .into(new ArrayList<>());
        boolean hasMore = loaded.size() > query.limit();
        List<Document> page = hasMore
                ? loaded.subList(0, query.limit())
                : loaded;
        List<SearchOperations.SearchHit> hits = page.stream()
                .map(AtlasStorySearchRepository::hit)
                .toList();
        String next = hasMore
                ? page.getLast().getString("paginationToken")
                : null;
        List<Document> metadata = stories.aggregate(
                        pipelines.facets(query))
                .maxTime(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .into(new ArrayList<>());
        return new SearchPage(
                hits,
                next,
                hasMore,
                metadata.isEmpty()
                        ? Map.of()
                        : facets(metadata.getFirst())
        );
    }

    static SearchOperations.SearchHit hit(Document value) {
        HomeStorySummary story = new HomeStorySummary(
                value.getString("_id"),
                value.getString("teamId"),
                value.getString("slug"),
                value.getString("title"),
                value.getString("coverAssetId"),
                instant(value.get("publishedAt"))
        );
        Number score = value.get("score", Number.class);
        return new SearchOperations.SearchHit(
                story,
                score == null ? 0 : score.doubleValue(),
                highlights(value)
        );
    }

    private static List<String> highlights(Document value) {
        List<Document> highlights = value.getList(
                "highlights",
                Document.class,
                List.of()
        );
        return highlights.stream()
                .flatMap(highlight -> highlight.getList(
                        "texts",
                        Document.class,
                        List.of()
                ).stream())
                .filter(text -> "hit".equals(text.getString("type")))
                .map(text -> text.getString("value"))
                .filter(Objects::nonNull)
                .limit(5)
                .toList();
    }

    static Map<String, Map<String, Long>> facets(Document metadata) {
        Document facet = metadata.get("facet", Document.class);
        if (facet == null) {
            return Map.of();
        }
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        for (String name : List.of(
                "categoryIds",
                "completionStatus",
                "origin"
        )) {
            Document data = facet.get(name, Document.class);
            if (data == null) {
                continue;
            }
            Map<String, Long> buckets = new LinkedHashMap<>();
            for (Document bucket : data.getList(
                    "buckets",
                    Document.class,
                    List.of()
            )) {
                Number count = bucket.get("count", Number.class);
                buckets.put(
                        Objects.toString(bucket.get("_id")),
                        count.longValue()
                );
            }
            result.put(name, Map.copyOf(buckets));
        }
        return Map.copyOf(result);
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        throw new IllegalStateException(
                "publishedAt is missing from search projection"
        );
    }
}
