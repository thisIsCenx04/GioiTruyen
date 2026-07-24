package com.storyplatform.catalog.infrastructure.persistence;

import com.storyplatform.catalog.application.PublicStoryProjection;
import com.storyplatform.catalog.application.port.StoryRepository;
import com.storyplatform.catalog.domain.Story;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

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
}
