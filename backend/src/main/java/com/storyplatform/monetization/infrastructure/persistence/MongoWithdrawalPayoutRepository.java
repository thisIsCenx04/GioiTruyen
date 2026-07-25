package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutRepository;
import com.storyplatform.monetization.domain.WithdrawalPayout;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class MongoWithdrawalPayoutRepository
        implements WithdrawalPayoutRepository {

    private final MongoTemplate mongo;

    public MongoWithdrawalPayoutRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public WithdrawalPayout insert(WithdrawalPayout payout) {
        try {
            return mongo.insert(
                    MongoWithdrawalPayoutDocument.from(payout)
            ).toDomain();
        } catch (DuplicateKeyException exception) {
            throw new WithdrawalException(
                    "Withdrawal payout changed concurrently.",
                    WithdrawalException.Kind.CONFLICT
            );
        }
    }

    @Override
    public Optional<WithdrawalPayout> findByWithdrawalId(
            String withdrawalId
    ) {
        return Optional.ofNullable(mongo.findById(
                withdrawalId,
                MongoWithdrawalPayoutDocument.class
        )).map(MongoWithdrawalPayoutDocument::toDomain);
    }

    @Override
    public Optional<WithdrawalPayout> claimRetryable(
            Instant now,
            Instant leaseUntil
    ) {
        Query query = Query.query(new Criteria().andOperator(
                        Criteria.where("state").is("PROCESSING"),
                        Criteria.where("nextAttemptAt").lte(now),
                        new Criteria().orOperator(
                                Criteria.where("leaseUntil").is(null),
                                Criteria.where("leaseUntil").lte(now)
                        )
                ))
                .with(Sort.by(
                        Sort.Order.asc("nextAttemptAt"),
                        Sort.Order.asc("_id")
                ));
        var claimed = mongo.findAndModify(
                query,
                new Update()
                        .inc("attempt", 1)
                        .set("leaseUntil", leaseUntil)
                        .unset("nextAttemptAt"),
                FindAndModifyOptions.options().returnNew(true),
                MongoWithdrawalPayoutDocument.class
        );
        return Optional.ofNullable(claimed)
                .map(MongoWithdrawalPayoutDocument::toDomain);
    }

    @Override
    public boolean update(
            WithdrawalPayout expected,
            WithdrawalPayout replacement
    ) {
        var value = MongoWithdrawalPayoutDocument.from(replacement);
        return mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(expected.withdrawalId()),
                        Criteria.where("state").is(expected.state().name()),
                        Criteria.where("attempt").is(expected.attempt())
                )),
                new Update()
                        .set("state", value.state())
                        .set("providerReference", value.providerReference())
                        .set("lastErrorCode", value.lastErrorCode())
                        .set("nextAttemptAt", value.nextAttemptAt())
                        .set("leaseUntil", value.leaseUntil())
                        .set("completedAt", value.completedAt())
                        .set(
                                "settlementTransactionId",
                                value.settlementTransactionId()
                        )
                        .set(
                                "releaseTransactionId",
                                value.releaseTransactionId()
                        ),
                MongoWithdrawalPayoutDocument.class
        ).getModifiedCount() == 1;
    }
}
