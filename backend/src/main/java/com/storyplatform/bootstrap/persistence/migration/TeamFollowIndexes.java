package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.teams.infrastructure.persistence
        .MongoTeamFollowDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class TeamFollowIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "aab4fc51bdbe7636c585250adbf82a24a03e30799cc62177dbf2f7cab12af352";

    @Override
    public long version() {
        return 11;
    }

    @Override
    public String name() {
        return "create unique team follow query indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoTeamFollowDocument.COLLECTION)
                .createIndex(new Index()
                        .named("team_follow_unique")
                        .on("teamId", Sort.Direction.ASC)
                        .on("userId", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoTeamFollowDocument.COLLECTION)
                .createIndex(new Index()
                        .named("team_follow_user_team")
                        .on("userId", Sort.Direction.ASC)
                        .on("teamId", Sort.Direction.ASC));
    }
}
