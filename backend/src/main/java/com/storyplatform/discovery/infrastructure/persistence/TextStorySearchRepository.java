package com.storyplatform.discovery.infrastructure.persistence;

import com.storyplatform.discovery.application.HomeStorySummary;
import com.storyplatform.discovery.application.SearchOperations;
import com.storyplatform.discovery.application.port.StorySearchRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TextStorySearchRepository
        implements StorySearchRepository {

    private static final String STORY_COLLECTION = "stories";

    private final MongoTemplate mongo;

    public TextStorySearchRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public SearchPage search(SearchQuery request) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("workflowStatus").is("PUBLISHED"));
        if (request.categoryId() != null) {
            filters.add(Criteria.where("categoryIds")
                    .is(request.categoryId()));
        }
        if (request.completionStatus() != null) {
            filters.add(Criteria.where("completionStatus")
                    .is(request.completionStatus()));
        }
        if (request.origin() != null) {
            filters.add(Criteria.where("origin").is(request.origin()));
        }
        TextQuery query = TextQuery.queryText(TextCriteria
                .forDefaultLanguage()
                .matching(request.text()));
        query.addCriteria(new Criteria().andOperator(
                filters.toArray(Criteria[]::new)
        ));
        query.sortByScore().limit(request.limit());
        query.fields().include(
                "_id",
                "teamId",
                "slug",
                "title",
                "coverAssetId",
                "publishedAt"
        );
        List<SearchOperations.SearchHit> hits = mongo.find(
                        query,
                        HomeStorySummary.class,
                        STORY_COLLECTION
                ).stream()
                .map(story -> new SearchOperations.SearchHit(
                        story,
                        0,
                        List.of()
                ))
                .toList();
        return new SearchPage(hits, null, false, Map.of());
    }
}
