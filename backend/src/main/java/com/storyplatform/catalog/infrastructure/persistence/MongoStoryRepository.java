package com.storyplatform.catalog.infrastructure.persistence;

import com.storyplatform.catalog.application.PublicStoryProjection;
import com.storyplatform.catalog.application.port.StoryRepository;
import com.storyplatform.catalog.domain.Story;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MongoStoryRepository implements StoryRepository {

    private final MongoTemplate mongo;

    public MongoStoryRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public boolean insertIfSlugAvailable(Story story) {
        Update insert = new Update()
                .setOnInsert("_id", story.id())
                .setOnInsert("teamId", story.teamId())
                .setOnInsert("slug", story.slug())
                .setOnInsert("title", story.title())
                .setOnInsert("aliases", story.aliases())
                .setOnInsert("synopsis", story.synopsis())
                .setOnInsert("categoryIds", story.categoryIds())
                .setOnInsert("origin", story.origin())
                .setOnInsert("language", story.language())
                .setOnInsert("completionStatus", story.completionStatus())
                .setOnInsert("workflowStatus", story.workflowStatus())
                .setOnInsert("currentRevision", story.currentRevision())
                .setOnInsert("coverAssetId", story.coverAssetId())
                .setOnInsert("publishedAt", story.publishedAt())
                .setOnInsert("createdAt", story.createdAt())
                .setOnInsert("updatedAt", story.updatedAt())
                .setOnInsert("version", story.version());
        return mongo.upsert(
                Query.query(Criteria.where("slug").is(story.slug())),
                insert,
                MongoStoryDocument.class
        ).getUpsertedId() != null;
    }

    @Override
    public Optional<PublicStoryProjection> findPublishedByIdOrSlug(
            String value
    ) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("workflowStatus").is(
                        Story.WorkflowStatus.PUBLISHED
                ),
                new Criteria().orOperator(
                        Criteria.where("_id").is(value),
                        Criteria.where("slug").is(value)
                )
        ));
        query.fields()
                .include(
                        "_id",
                        "teamId",
                        "slug",
                        "title",
                        "synopsis",
                        "categoryIds",
                        "origin",
                        "language",
                        "completionStatus",
                        "publishedAt",
                        "updatedAt",
                        "version"
                );
        return Optional.ofNullable(mongo.findOne(
                query,
                PublicStoryProjection.class,
                MongoStoryDocument.COLLECTION
        ));
    }

    @Override
    public List<PublicStoryProjection> findPublished(StoryListQuery request) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("workflowStatus").is(
                Story.WorkflowStatus.PUBLISHED
        ));
        if (!request.categoryIds().isEmpty()) {
            filters.add(Criteria.where("categoryIds").all(
                    request.categoryIds()
            ));
        }
        if (request.completionStatus() != null) {
            filters.add(Criteria.where("completionStatus").is(
                    request.completionStatus()
            ));
        }
        if (request.origin() != null) {
            filters.add(Criteria.where("origin").is(request.origin()));
        }
        if (request.teamId() != null) {
            filters.add(Criteria.where("teamId").is(request.teamId()));
        }
        if (request.afterValue() != null) {
            filters.add(new Criteria().orOperator(
                    Criteria.where(request.sort().field())
                            .lt(request.afterValue()),
                    new Criteria().andOperator(
                            Criteria.where(request.sort().field())
                                    .is(request.afterValue()),
                            Criteria.where("_id").lt(request.afterId())
                    )
            ));
        }
        Query query = Query.query(new Criteria().andOperator(
                        filters.toArray(Criteria[]::new)
                ))
                .with(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc(
                                request.sort().field()
                        ),
                        org.springframework.data.domain.Sort.Order.desc("_id")
                ))
                .limit(request.limit());
        includePublicFields(query);
        return mongo.find(
                query,
                PublicStoryProjection.class,
                MongoStoryDocument.COLLECTION
        );
    }

    private static void includePublicFields(Query query) {
        query.fields().include(
                "_id",
                "teamId",
                "slug",
                "title",
                "synopsis",
                "categoryIds",
                "origin",
                "language",
                "completionStatus",
                "publishedAt",
                "updatedAt",
                "version"
        );
    }
}
