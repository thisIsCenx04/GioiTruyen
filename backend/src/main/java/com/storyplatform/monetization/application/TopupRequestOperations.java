package com.storyplatform.monetization.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface TopupRequestOperations {

    TopupView create(
            String userId,
            String idempotencyKey,
            long amountVnd
    );

    TopupView get(String userId, String requestId);

    List<TopupView> recent(String userId);

    record TopupView(
            String id,
            long amountVnd,
            long creditedXu,
            BigDecimal discountPercent,
            long discountVersion,
            String transferReference,
            String qrPayload,
            String status,
            Instant expiresAt,
            Instant createdAt
    ) {
    }
}
