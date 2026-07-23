package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.teams.infrastructure.persistence
        .MongoTeamInvitationDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.time.Duration;

public final class TeamInvitationIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "73a4938579b4ab06f95f14007fd6fa5ef4f89d83f5749cdff60553516c0a997c";

    @Override
    public long version() {
        return 10;
    }

    @Override
    public String name() {
        return "create team invitation safety indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoTeamInvitationDocument.COLLECTION)
                .createIndex(new Index()
                        .named("team_invitation_token_unique")
                        .on("tokenHash", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoTeamInvitationDocument.COLLECTION)
                .createIndex(new Index()
                        .named("team_invitation_idempotency_unique")
                        .on("teamId", Sort.Direction.ASC)
                        .on("idempotencyKey", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoTeamInvitationDocument.COLLECTION)
                .createIndex(new Index()
                        .named("team_invitation_target_unique")
                        .on("teamId", Sort.Direction.ASC)
                        .on("targetUserId", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoTeamInvitationDocument.COLLECTION)
                .createIndex(new Index()
                        .named("team_invitation_expiry_ttl")
                        .on("expiresAt", Sort.Direction.ASC)
                        .expire(Duration.ZERO));
    }
}
