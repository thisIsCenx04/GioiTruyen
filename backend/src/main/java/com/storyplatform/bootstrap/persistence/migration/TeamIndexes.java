package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.teams.infrastructure.persistence.MongoTeamDocument;
import com.storyplatform.teams.infrastructure.persistence.MongoTeamMembershipDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class TeamIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "7dff4b979f13f57784001ddf2a74a92079e235488318c228f524455707f46939";

    @Override
    public long version() {
        return 9;
    }

    @Override
    public String name() {
        return "create team and membership query indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoTeamDocument.COLLECTION)
                .createIndex(new Index()
                        .named("team_slug_unique")
                        .on("slug", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoTeamDocument.COLLECTION)
                .createIndex(new Index()
                        .named("team_public_list")
                        .on("state", Sort.Direction.ASC)
                        .on("updatedAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC));
        mongo.indexOps(MongoTeamMembershipDocument.COLLECTION)
                .createIndex(new Index()
                        .named("membership_team_user_unique")
                        .on("teamId", Sort.Direction.ASC)
                        .on("userId", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoTeamMembershipDocument.COLLECTION)
                .createIndex(new Index()
                        .named("membership_user_state_team")
                        .on("userId", Sort.Direction.ASC)
                        .on("state", Sort.Direction.ASC)
                        .on("teamId", Sort.Direction.ASC));
    }
}
