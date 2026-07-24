package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.publishing.infrastructure.persistence
        .MongoPublishingScheduleDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

public final class PublishingScheduleIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "f58fca4a75d2170ff884cd61b83046df736fbf2edbe7c91b0ec3697c92e93550";

    @Override
    public long version() {
        return 27;
    }

    @Override
    public String name() {
        return "index publishing schedules";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        var indexes = mongo.indexOps(
                MongoPublishingScheduleDocument.COLLECTION
        );
        indexes.createIndex(new Index()
                .named("schedule_active_target_unique")
                .on("targetType", Sort.Direction.ASC)
                .on("targetId", Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(
                        Criteria.where("state").is("SCHEDULED")
                )));
        indexes.createIndex(new Index()
                .named("schedule_due_claim")
                .on("state", Sort.Direction.ASC)
                .on("publishAt", Sort.Direction.ASC)
                .on("_id", Sort.Direction.ASC));
        indexes.createIndex(new Index()
                .named("schedule_team_target")
                .on("teamId", Sort.Direction.ASC)
                .on("targetId", Sort.Direction.ASC)
                .on("state", Sort.Direction.ASC));
    }
}
