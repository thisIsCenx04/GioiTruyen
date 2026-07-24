package com.storyplatform.unit.monetization.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.monetization.application.port.TopupRejectionRepository;
import com.storyplatform.monetization.domain.PaymentEvent;
import com.storyplatform.monetization.domain.TopupRequest;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoManualTopupAuditDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoPaymentEventDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupRejectionRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupRequestDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoTopupRejectionRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T00:00:00Z");

    @Test
    void atomicallyRejectsBothDocumentsBeforeAudit() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoTopupRequestDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoPaymentEventDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoTopupRejectionRepository(mongo);

        assertThat(repository.reject(
                review(),
                "admin",
                "AMOUNT_MISMATCH",
                "Settlement evidence verified",
                "evidence/bank-statement-1",
                NOW
        )).isTrue();
        verify(mongo).insert(any(MongoManualTopupAuditDocument.class));
    }

    @Test
    void reportsCasLossWithoutWritingAudit() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoTopupRequestDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 0L, null));

        assertThat(new MongoTopupRejectionRepository(mongo).reject(
                review(),
                "admin",
                "AMOUNT_MISMATCH",
                "Settlement evidence verified",
                "evidence/bank-statement-1",
                NOW
        )).isFalse();
    }

    @Test
    void returnsEmptyWhenReviewTargetDoesNotExist() {
        MongoTemplate mongo = mock(MongoTemplate.class);

        assertThat(new MongoTopupRejectionRepository(mongo).find(
                "missing-topup"
        )).isEmpty();
    }

    private static TopupRejectionRepository.Review review() {
        return new TopupRejectionRepository.Review(
                new TopupRequest(
                        "20000000-0000-4000-8000-000000000001",
                        "reader",
                        100_000,
                        90_000,
                        BigDecimal.TEN,
                        1,
                        "GT12345678901234",
                        "qr",
                        TopupRequest.Status.PENDING_REVIEW,
                        NOW,
                        NOW.minusSeconds(1800),
                        "a".repeat(64),
                        "b".repeat(64)
                ),
                new PaymentEvent(
                        "30000000-0000-4000-8000-000000000001",
                        "bank",
                        "event-1",
                        "bank-ref",
                        100_000,
                        "GT12345678901234",
                        NOW.minusSeconds(60),
                        NOW.minusSeconds(30),
                        "c".repeat(64),
                        "{}",
                        PaymentEvent.Status.PENDING_REVIEW
                )
        );
    }
}
