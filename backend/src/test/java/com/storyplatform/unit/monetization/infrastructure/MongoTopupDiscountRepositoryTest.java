package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.TopupDiscountException;
import com.storyplatform.monetization.application.port
        .TopupDiscountRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoMonetizationConfigAuditDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupDiscountDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupDiscountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoTopupDiscountRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");

    @Test
    void readsLatestAndAppendsConfigurationWithAudit() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var document = new MongoTopupDiscountDocument(
                "topup-discount:1",
                "topup-discount",
                new BigDecimal("12.50"),
                1,
                NOW,
                "admin"
        );
        when(mongo.findOne(
                any(Query.class),
                eq(MongoTopupDiscountDocument.class)
        )).thenReturn(document);
        when(mongo.insert(any(MongoTopupDiscountDocument.class)))
                .thenReturn(document);
        var repository = new MongoTopupDiscountRepository(mongo);

        assertThat(repository.current()).get()
                .extracting("version", "discountPercent")
                .containsExactly(1L, new BigDecimal("12.50"));
        var stored = repository.insert(
                value(),
                BigDecimal.TEN,
                "Seasonal operating policy"
        );

        assertThat(stored.version()).isEqualTo(1);
        verify(mongo).insert(any(MongoMonetizationConfigAuditDocument.class));
    }

    @Test
    void mapsUniqueVersionRaceToBusinessConflict() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(MongoTopupDiscountDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        var repository = new MongoTopupDiscountRepository(mongo);

        assertThatThrownBy(() -> repository.insert(
                value(),
                BigDecimal.TEN,
                "Seasonal operating policy"
        )).isInstanceOf(TopupDiscountException.class)
                .extracting("kind")
                .isEqualTo(TopupDiscountException.Kind.CONFLICT);
    }

    private static TopupDiscountRepository.VersionedDiscount value() {
        return new TopupDiscountRepository.VersionedDiscount(
                "topup-discount",
                new BigDecimal("12.50"),
                1,
                NOW,
                "admin"
        );
    }
}
