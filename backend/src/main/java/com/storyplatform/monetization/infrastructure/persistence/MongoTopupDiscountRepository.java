package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.TopupDiscountException;
import com.storyplatform.monetization.application.port
        .TopupDiscountRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MongoTopupDiscountRepository
        implements TopupDiscountRepository {

    private final MongoTemplate mongo;

    public MongoTopupDiscountRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<VersionedDiscount> current() {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("key").is("topup-discount"))
                        .with(Sort.by(Sort.Direction.DESC, "version"))
                        .limit(1),
                MongoTopupDiscountDocument.class
        )).map(MongoTopupDiscountDocument::toDomain);
    }

    @Override
    public VersionedDiscount insert(
            VersionedDiscount discount,
            BigDecimal previousPercent,
            String reason
    ) {
        try {
            var stored = mongo.insert(
                    MongoTopupDiscountDocument.from(discount)
            );
            mongo.insert(new MongoMonetizationConfigAuditDocument(
                    UUID.randomUUID().toString(),
                    "monetization",
                    "topup_discount.changed",
                    "configuration",
                    discount.key(),
                    discount.changedBy(),
                    previousPercent,
                    discount.discountPercent(),
                    discount.version(),
                    reason,
                    discount.effectiveAt()
            ));
            return stored.toDomain();
        } catch (DuplicateKeyException exception) {
            throw new TopupDiscountException(
                    "The discount configuration version is stale.",
                    TopupDiscountException.Kind.CONFLICT
            );
        }
    }
}
