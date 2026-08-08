package com.storyplatform.monetization.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface TopupDiscountRepository {

    Optional<VersionedDiscount> current();

    VersionedDiscount insert(
            VersionedDiscount discount,
            BigDecimal previousPercent,
            String reason
    );

    record VersionedDiscount(
            String key,
            BigDecimal discountPercent,
            long version,
            Instant effectiveAt,
            String changedBy
    ) {
    }
}
