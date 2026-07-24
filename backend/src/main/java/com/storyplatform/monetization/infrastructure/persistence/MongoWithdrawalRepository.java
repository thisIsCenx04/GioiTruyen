package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application.port
        .WithdrawalCursorCodec;
import com.storyplatform.monetization.application.port.WithdrawalRepository;
import com.storyplatform.monetization.domain.Withdrawal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MongoWithdrawalRepository
        implements WithdrawalRepository {

    private final MongoTemplate mongo;

    public MongoWithdrawalRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<Withdrawal> findByIdempotencyKeyHash(
            String keyHash
    ) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("idempotencyKeyHash").is(keyHash)),
                MongoWithdrawalDocument.class
        )).map(MongoWithdrawalDocument::toDomain);
    }

    @Override
    public Withdrawal insert(Withdrawal withdrawal) {
        try {
            return mongo.insert(
                    MongoWithdrawalDocument.from(withdrawal)
            ).toDomain();
        } catch (DuplicateKeyException exception) {
            throw new WithdrawalException(
                    "Withdrawal changed concurrently.",
                    WithdrawalException.Kind.CONFLICT
            );
        }
    }

    @Override
    public List<Withdrawal> findByTeam(
            String teamId,
            WithdrawalCursorCodec.Position after,
            int limit
    ) {
        Criteria criteria = Criteria.where("teamId").is(teamId);
        if (after != null) {
            criteria = new Criteria().andOperator(
                    criteria,
                    new Criteria().orOperator(
                            Criteria.where("createdAt")
                                    .lt(after.createdAt()),
                            new Criteria().andOperator(
                                    Criteria.where("createdAt")
                                            .is(after.createdAt()),
                                    Criteria.where("_id").lt(after.id())
                            )
                    )
            );
        }
        return mongo.find(
                Query.query(criteria)
                        .with(Sort.by(
                                Sort.Order.desc("createdAt"),
                                Sort.Order.desc("_id")
                        ))
                        .limit(limit),
                MongoWithdrawalDocument.class
        ).stream().map(MongoWithdrawalDocument::toDomain).toList();
    }
}
