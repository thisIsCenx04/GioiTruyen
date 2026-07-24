package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.port
        .WithdrawalDestinationDirectory;
import com.storyplatform.monetization.domain.Withdrawal;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class MongoWithdrawalDestinationDirectory
        implements WithdrawalDestinationDirectory {

    private final MongoTemplate mongo;

    public MongoWithdrawalDestinationDirectory(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<Withdrawal.DestinationSnapshot> findEligible(
            String teamId,
            String destinationId,
            Instant now
    ) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(destinationId),
                        Criteria.where("teamId").is(teamId),
                        Criteria.where("state").is("VERIFIED"),
                        Criteria.where("availableAt").lte(now)
                )),
                MongoWithdrawalDestinationDocument.class
        )).map(MongoWithdrawalDestinationDocument::snapshot);
    }
}
