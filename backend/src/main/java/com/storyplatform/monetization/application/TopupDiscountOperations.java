package com.storyplatform.monetization.application;

import java.math.BigDecimal;
import java.time.Instant;

public interface TopupDiscountOperations {

    DiscountView current();

    DiscountView update(
            String actorId,
            String reauthenticationToken,
            long expectedVersion,
            BigDecimal discountPercent,
            String reason
    );

    record DiscountView(
            BigDecimal discountPercent,
            long version,
            Instant effectiveAt,
            String changedBy
    ) {
    }
}
