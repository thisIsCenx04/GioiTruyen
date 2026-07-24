package com.storyplatform.discovery.infrastructure.persistence;

import com.storyplatform.discovery.application.HomeStorySummary;
import com.storyplatform.discovery.application.port.HomeStorySource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
public class MongoHomeStorySource implements HomeStorySource {

    private static final String STORY_COLLECTION = "stories";

    private final MongoTemplate mongo;

    public MongoHomeStorySource(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public List<HomeStorySummary> find(
            Filter filter,
            int limit
    ) {
        Objects.requireNonNull(filter, "filter");
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("workflowStatus").is(
                "PUBLISHED"
        ));
        if (filter == Filter.COMPLETED) {
            filters.add(Criteria.where("completionStatus").is("COMPLETED"));
        }
        if (filter == Filter.ORIGINAL) {
            filters.add(Criteria.where("origin").is("ORIGINAL"));
        }
        Query query = Query.query(new Criteria().andOperator(
                        filters.toArray(Criteria[]::new)
                ))
                .with(Sort.by(
                        Sort.Order.desc("publishedAt"),
                        Sort.Order.desc("_id")
                ))
                .limit(limit);
        query.fields().include(
                "_id",
                "teamId",
                "slug",
                "title",
                "coverAssetId",
                "publishedAt"
        );
        return mongo.find(
                query,
                HomeStorySummary.class,
                STORY_COLLECTION
        );
    }
}
