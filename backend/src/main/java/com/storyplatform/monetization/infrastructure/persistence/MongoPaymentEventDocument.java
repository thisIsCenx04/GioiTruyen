package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.PaymentEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoPaymentEventDocument.COLLECTION)
public record MongoPaymentEventDocument(
        @Id String id,
        String provider,
        String providerEventId,
        String bankReference,
        long amountVnd,
        String transferReference,
        Instant occurredAt,
        Instant receivedAt,
        String payloadHash,
        String rawPayload,
        String status
) {
    public static final String COLLECTION = "payment_events";

    static MongoPaymentEventDocument from(PaymentEvent value) {
        return new MongoPaymentEventDocument(
                value.id(),
                value.provider(),
                value.providerEventId(),
                value.bankReference(),
                value.amountVnd(),
                value.transferReference(),
                value.occurredAt(),
                value.receivedAt(),
                value.payloadHash(),
                value.rawPayload(),
                value.status().name()
        );
    }
}
