package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.port
        .TopupDiscountRepository;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = MongoTopupDiscountDocument.COLLECTION)
public record MongoTopupDiscountDocument(
        @Id String id,
        String key,
        BigDecimal discountPercent,
        long version,
        Instant effectiveAt,
        String changedBy
) {
    public static final String COLLECTION = "monetization_configs";

    static MongoTopupDiscountDocument from(
            TopupDiscountRepository.VersionedDiscount value
    ) {
        return new MongoTopupDiscountDocument(
                value.key() + ":" + value.version(),
                value.key(),
                value.discountPercent(),
                value.version(),
                value.effectiveAt(),
                value.changedBy()
        );
    }

    TopupDiscountRepository.VersionedDiscount toDomain() {
        return new TopupDiscountRepository.VersionedDiscount(
                key,
                discountPercent,
                version,
                effectiveAt,
                changedBy
        );
    }
}
