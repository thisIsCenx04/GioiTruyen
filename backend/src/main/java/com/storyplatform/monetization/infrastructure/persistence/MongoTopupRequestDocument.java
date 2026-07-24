package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.TopupRequest;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = MongoTopupRequestDocument.COLLECTION)
public record MongoTopupRequestDocument(
        @Id String id,
        String userId,
        long amountVnd,
        long creditedXu,
        BigDecimal discountPercent,
        long discountVersion,
        String transferReference,
        String qrPayload,
        String status,
        Instant expiresAt,
        Instant createdAt,
        String idempotencyKeyHash,
        String requestHash
) {
    public static final String COLLECTION = "topup_requests";

    static MongoTopupRequestDocument from(TopupRequest value) {
        return new MongoTopupRequestDocument(
                value.id(),
                value.userId(),
                value.amountVnd(),
                value.creditedXu(),
                value.discountPercent(),
                value.discountVersion(),
                value.transferReference(),
                value.qrPayload(),
                value.status().name(),
                value.expiresAt(),
                value.createdAt(),
                value.idempotencyKeyHash(),
                value.requestHash()
        );
    }

    TopupRequest toDomain() {
        return new TopupRequest(
                id,
                userId,
                amountVnd,
                creditedXu,
                discountPercent,
                discountVersion,
                transferReference,
                qrPayload,
                TopupRequest.Status.valueOf(status),
                expiresAt,
                createdAt,
                idempotencyKeyHash,
                requestHash
        );
    }
}
