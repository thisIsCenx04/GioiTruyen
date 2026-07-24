package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.port.RewardRepository;
import com.storyplatform.monetization.application.RewardException;
import com.storyplatform.monetization.domain.RewardPeriod;
import com.storyplatform.monetization.domain.RewardAdjustment;
import com.storyplatform.monetization.domain.RewardSettlement;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MongoRewardRepository implements RewardRepository {

    private final MongoTemplate mongo;

    public MongoRewardRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<RewardPeriod> findPeriod(String periodId) {
        return Optional.ofNullable(mongo.findById(
                periodId,
                MongoRewardPeriodDocument.class
        )).map(MongoRewardPeriodDocument::toDomain);
    }

    @Override
    public LockResult lock(
            RewardPeriod period,
            List<RewardSettlement> settlements
    ) {
        try {
            mongo.insert(MongoRewardPeriodDocument.from(period));
            if (!settlements.isEmpty()) {
                mongo.insert(
                        settlements.stream()
                                .map(MongoRewardSettlementDocument::from)
                                .toList(),
                        MongoRewardSettlementDocument.class
                );
            }
            return new LockResult(period, true);
        } catch (DuplicateKeyException exception) {
            throw new RewardException(
                    "Reward period was locked concurrently; retry safely.",
                    RewardException.Kind.CONFLICT
            );
        }
    }

    @Override
    public List<RewardSettlement> findByPeriod(String periodId) {
        return mongo.find(
                Query.query(Criteria.where("periodId").is(periodId))
                        .with(Sort.by("teamId", "_id")),
                MongoRewardSettlementDocument.class
        ).stream().map(MongoRewardSettlementDocument::toDomain).toList();
    }

    @Override
    public List<RewardSettlement> findRecentByTeam(
            String teamId,
            int limit
    ) {
        return mongo.find(
                Query.query(Criteria.where("teamId").is(teamId))
                        .with(Sort.by(
                                Sort.Order.desc("periodId"),
                                Sort.Order.desc("_id")
                        ))
                        .limit(limit),
                MongoRewardSettlementDocument.class
        ).stream().map(MongoRewardSettlementDocument::toDomain).toList();
    }

    @Override
    public Optional<RewardSettlement> findSettlement(String settlementId) {
        return Optional.ofNullable(mongo.findById(
                settlementId,
                MongoRewardSettlementDocument.class
        )).map(MongoRewardSettlementDocument::toDomain);
    }

    @Override
    public Optional<RewardAdjustment> findAdjustmentByKeyHash(
            String keyHash
    ) {
        return findAdjustment(Criteria.where(
                "idempotencyKeyHash"
        ).is(keyHash));
    }

    @Override
    public Optional<RewardAdjustment> findAdjustmentBySettlement(
            String settlementId
    ) {
        return findAdjustment(Criteria.where(
                "settlementId"
        ).is(settlementId));
    }

    @Override
    public RewardAdjustment insertAdjustment(
            RewardAdjustment adjustment
    ) {
        try {
            return mongo.insert(
                    MongoRewardAdjustmentDocument.from(adjustment)
            ).toDomain();
        } catch (DuplicateKeyException exception) {
            throw new RewardException(
                    "Reward adjustment changed concurrently; retry safely.",
                    RewardException.Kind.CONFLICT
            );
        }
    }

    @Override
    public boolean markNoReward(String settlementId, Instant at) {
        return updateSettlement(
                settlementId,
                new Update()
                        .set("state", RewardSettlement.State.NO_REWARD)
                        .set("postedAt", at)
        );
    }

    @Override
    public boolean markPosted(
            String settlementId,
            String ledgerTransactionId,
            Instant at
    ) {
        return updateSettlement(
                settlementId,
                new Update()
                        .set("state", RewardSettlement.State.POSTED)
                        .set("ledgerTransactionId", ledgerTransactionId)
                        .set("postedAt", at)
        );
    }

    @Override
    public boolean markPeriodSettled(String periodId, Instant at) {
        return mongo.updateFirst(
                Query.query(Criteria.where("_id").is(periodId)
                        .and("state").is(RewardPeriod.State.LOCKED)),
                new Update()
                        .set("state", RewardPeriod.State.SETTLED)
                        .set("settledAt", at),
                MongoRewardPeriodDocument.class
        ).getModifiedCount() == 1;
    }

    private boolean updateSettlement(String id, Update update) {
        return mongo.updateFirst(
                Query.query(Criteria.where("_id").is(id)
                        .and("state").is(
                                RewardSettlement.State.PENDING
                        )),
                update,
                MongoRewardSettlementDocument.class
        ).getModifiedCount() == 1;
    }

    private Optional<RewardAdjustment> findAdjustment(
            Criteria criteria
    ) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(criteria),
                MongoRewardAdjustmentDocument.class
        )).map(MongoRewardAdjustmentDocument::toDomain);
    }
}
