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
import org.springframework.data.mongodb.core.query.Update;

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
    public Optional<Withdrawal> findById(String withdrawalId) {
        return Optional.ofNullable(mongo.findById(
                withdrawalId,
                MongoWithdrawalDocument.class
        )).map(MongoWithdrawalDocument::toDomain);
    }

    @Override
    public Optional<Withdrawal> findByReviewKeyHash(String keyHash) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("reviewKeyHash").is(keyHash)),
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
    public boolean decide(Withdrawal decision) {
        return mongo.updateFirst(
                Query.query(Criteria.where("_id").is(decision.id())
                        .and("state").is("PENDING_REVIEW")),
                new Update()
                        .set("state", decision.state().name())
                        .set("reviewedBy", decision.reviewedBy())
                        .set("reviewReason", decision.reviewReason())
                        .set(
                                "reviewRiskLevel",
                                decision.reviewRiskLevel()
                        )
                        .set(
                                "reviewRiskRuleVersion",
                                decision.reviewRiskRuleVersion()
                        )
                        .set("reviewKeyHash", decision.reviewKeyHash())
                        .set(
                                "reviewRequestHash",
                                decision.reviewRequestHash()
                        )
                        .set(
                                "releaseTransactionId",
                                decision.releaseTransactionId()
                        )
                        .set("reviewedAt", decision.reviewedAt()),
                MongoWithdrawalDocument.class
        ).getModifiedCount() == 1;
    }

    @Override
    public Optional<Withdrawal> findOldestApproved() {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("state").is("APPROVED"))
                        .with(Sort.by(
                                Sort.Order.asc("reviewedAt"),
                                Sort.Order.asc("_id")
                        )),
                MongoWithdrawalDocument.class
        )).map(MongoWithdrawalDocument::toDomain);
    }

    @Override
    public boolean transition(
            String withdrawalId,
            Withdrawal.State expected,
            Withdrawal.State target,
            String releaseTransactionId
    ) {
        Update update = new Update().set("state", target.name());
        if (releaseTransactionId != null) {
            update.set("releaseTransactionId", releaseTransactionId);
        }
        return mongo.updateFirst(
                Query.query(Criteria.where("_id").is(withdrawalId)
                        .and("state").is(expected.name())),
                update,
                MongoWithdrawalDocument.class
        ).getModifiedCount() == 1;
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
