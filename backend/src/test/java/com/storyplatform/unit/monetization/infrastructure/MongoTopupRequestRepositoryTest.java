package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.TopupRequestException;
import com.storyplatform.monetization.domain.TopupRequest;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupRequestDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoTopupRequestRepositoryTest {

    @Test
    void roundTripsAndUsesOwnerBoundQueries() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        TopupRequest request = request();
        var document = document();
        when(mongo.insert(any(MongoTopupRequestDocument.class)))
                .thenReturn(document);
        when(mongo.findOne(
                any(Query.class),
                eq(MongoTopupRequestDocument.class)
        )).thenReturn(document);
        when(mongo.find(
                any(Query.class),
                eq(MongoTopupRequestDocument.class)
        )).thenReturn(List.of(document));
        var repository = new MongoTopupRequestRepository(mongo);

        assertThat(repository.insert(request)).isEqualTo(request);
        assertThat(repository.findByIdempotencyKeyHash("a".repeat(64)))
                .contains(request);
        assertThat(repository.findByIdAndUserId(request.id(), "reader"))
                .contains(request);
        assertThat(repository.findRecentByUserId("reader", 50))
                .containsExactly(request);
    }

    @Test
    void mapsUniqueIndexRacesToConflict() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(MongoTopupRequestDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() ->
                new MongoTopupRequestRepository(mongo).insert(request())
        ).isInstanceOf(TopupRequestException.class)
                .extracting("kind")
                .isEqualTo(TopupRequestException.Kind.CONFLICT);
    }

    private static TopupRequest request() {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        return new TopupRequest(
                "10000000-0000-4000-8000-000000000001",
                "reader",
                100_000,
                90_000,
                BigDecimal.TEN,
                0,
                "GT12345678901234",
                "server-qr",
                TopupRequest.Status.AWAITING_PAYMENT,
                now.plusSeconds(1800),
                now,
                "a".repeat(64),
                "b".repeat(64)
        );
    }

    private static MongoTopupRequestDocument document() {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        return new MongoTopupRequestDocument(
                "10000000-0000-4000-8000-000000000001",
                "reader",
                100_000,
                90_000,
                BigDecimal.TEN,
                0,
                "GT12345678901234",
                "server-qr",
                "AWAITING_PAYMENT",
                now.plusSeconds(1800),
                now,
                "a".repeat(64),
                "b".repeat(64)
        );
    }
}
