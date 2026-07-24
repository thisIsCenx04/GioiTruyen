package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.moderation.infrastructure.persistence
        .MongoCommunityReportRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class CommunityReportIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "d64c6605e6c1ae3f284f021519a0094a2d0edc25cdd4df7842cad1562c772698";

    @Override
    public long version() {
        return 36;
    }

    @Override
    public String name() {
        return "index deduplicated community reports";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoCommunityReportRepository.COLLECTION)
                .createIndex(new Index()
                        .named("report_moderation_queue")
                        .on("status", Sort.Direction.ASC)
                        .on("riskScore", Sort.Direction.DESC)
                        .on("createdAt", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC));
        mongo.indexOps(MongoCommunityReportRepository.COLLECTION)
                .createIndex(new Index()
                        .named("report_reporter_created")
                        .on("reporterId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC));
        mongo.indexOps(MongoCommunityReportRepository.COLLECTION)
                .createIndex(new Index()
                        .named("report_target")
                        .on("targetType", Sort.Direction.ASC)
                        .on("targetId", Sort.Direction.ASC));
    }
}
