package com.storyplatform.catalog.infrastructure.persistence;

import com.storyplatform.catalog.application.PublicChapterProjection;
import com.storyplatform.catalog.application.port.ChapterRepository;
import com.storyplatform.catalog.domain.Chapter;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
public class MongoChapterRepository implements ChapterRepository {

    private final MongoTemplate mongo;

    public MongoChapterRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public List<PublicChapterProjection> findPublished(
            ChapterListQuery request
    ) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("storyId").is(request.storyId()));
        filters.add(Criteria.where("workflowStatus").is(
                Chapter.WorkflowStatus.PUBLISHED
        ));
        if (request.afterNumber() != null) {
            filters.add(new Criteria().orOperator(
                    Criteria.where("number").gt(request.afterNumber()),
                    new Criteria().andOperator(
                            Criteria.where("number")
                                    .is(request.afterNumber()),
                            Criteria.where("_id").gt(request.afterId())
                    )
            ));
        }
        Query query = Query.query(new Criteria().andOperator(
                        filters.toArray(Criteria[]::new)
                ))
                .with(Sort.by(
                        Sort.Order.asc("number"),
                        Sort.Order.asc("_id")
                ))
                .limit(request.limit());
        query.fields().include(
                "_id",
                "storyId",
                "number",
                "slug",
                "title",
                "publishedAt",
                "version"
        );
        return mongo.find(
                query,
                PublicChapterProjection.class,
                MongoChapterDocument.COLLECTION
        );
    }
}
