package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.ReferralException;
import com.storyplatform.monetization.application.port.ReferralRepository;
import com.storyplatform.monetization.domain.ReferralAttribution;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MongoReferralRepository
        implements ReferralRepository {

    private final MongoTemplate mongo;

    public MongoReferralRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<ReferralAttribution> findByRefereeId(String refereeId) {
        return find(Criteria.where("refereeId").is(refereeId))
                .map(MongoReferralAttributionDocument::toDomain);
    }

    @Override
    public Optional<StoredAttribution> findByIdempotencyKeyHash(
            String keyHash
    ) {
        return find(Criteria.where("idempotencyKeyHash").is(keyHash))
                .map(value -> new StoredAttribution(
                        value.toDomain(),
                        value.requestHash()
                ));
    }

    @Override
    public ReferralAttribution insert(
            ReferralAttribution attribution,
            String idempotencyKeyHash,
            String requestHash
    ) {
        try {
            return mongo.insert(MongoReferralAttributionDocument.from(
                    attribution,
                    idempotencyKeyHash,
                    requestHash
            )).toDomain();
        } catch (DuplicateKeyException exception) {
            throw new ReferralException(
                    "Referral attribution changed concurrently.",
                    ReferralException.Kind.CONFLICT
            );
        }
    }

    @Override
    public long countRecentByReferrer(
            String referrerId,
            Instant since
    ) {
        return mongo.count(
                Query.query(Criteria.where("referrerId").is(referrerId)
                        .and("attributedAt").gte(since)),
                MongoReferralAttributionDocument.class
        );
    }

    @Override
    public List<ReferralAttribution> findRewardable(
            Instant now,
            int limit
    ) {
        return mongo.find(
                Query.query(Criteria.where("state").is("PENDING")
                                .and("eligibleAt").lte(now))
                        .with(Sort.by("eligibleAt", "_id"))
                        .limit(limit),
                MongoReferralAttributionDocument.class
        ).stream().map(MongoReferralAttributionDocument::toDomain).toList();
    }

    @Override
    public boolean markRewarded(
            String attributionId,
            String ledgerTransactionId,
            Instant rewardedAt
    ) {
        return mongo.updateFirst(
                Query.query(Criteria.where("_id").is(attributionId)
                        .and("state").is("PENDING")
                        .and("eligibleAt").lte(rewardedAt)),
                new Update()
                        .set("state", "REWARDED")
                        .set("ledgerTransactionId", ledgerTransactionId)
                        .set("rewardedAt", rewardedAt),
                MongoReferralAttributionDocument.class
        ).getModifiedCount() == 1;
    }

    @Override
    public Summary summary(String userId) {
        List<Document> result = new ArrayList<>(1);
        mongo.getCollection(MongoReferralAttributionDocument.COLLECTION)
                .aggregate(summaryPipeline(userId))
                .forEach(result::add);
        if (result.isEmpty()) {
            return new Summary(0, 0, 0, 0);
        }
        Document value = result.getFirst();
        return new Summary(
                number(value, "referredCount"),
                number(value, "pendingCount"),
                number(value, "rewardedCount"),
                number(value, "rewardedXu")
        );
    }

    private Optional<MongoReferralAttributionDocument> find(
            Criteria criteria
    ) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(criteria),
                MongoReferralAttributionDocument.class
        ));
    }

    static List<Document> summaryPipeline(String userId) {
        return List.of(
                new Document("$match", new Document(
                        "referrerId", userId
                )),
                new Document("$group", new Document("_id", null)
                        .append("referredCount", new Document("$sum", 1))
                        .append("pendingCount", conditionalCount("PENDING"))
                        .append("rewardedCount", conditionalCount("REWARDED"))
                        .append("rewardedXu", new Document(
                                "$sum",
                                new Document("$cond", List.of(
                                        new Document("$eq", List.of(
                                                "$state", "REWARDED"
                                        )),
                                        "$rewardXu",
                                        0
                                ))
                        )))
        );
    }

    private static Document conditionalCount(String state) {
        return new Document("$sum", new Document("$cond", List.of(
                new Document("$eq", List.of("$state", state)),
                1,
                0
        )));
    }

    private static long number(Document value, String field) {
        Object number = value.get(field);
        return number instanceof Number result ? result.longValue() : 0;
    }
}
