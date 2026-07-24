package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.DonationException;
import com.storyplatform.monetization.domain.Donation;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoDonationDocument;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoDonationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoDonationRepositoryTest {

    @Test
    void roundTripsInsertedDonationAndReturnsEmptyForUnknownKey() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(MongoDonationDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var repository = new MongoDonationRepository(mongo);

        assertThat(repository.insert(donation())).isEqualTo(donation());
        assertThat(repository.findByIdempotencyKeyHash("a".repeat(64)))
                .isEmpty();
    }

    @Test
    void mapsUniqueIndexRaceToConflict() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.insert(any(MongoDonationDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() ->
                new MongoDonationRepository(mongo).insert(donation())
        ).isInstanceOf(DonationException.class)
                .extracting("kind")
                .isEqualTo(DonationException.Kind.CONFLICT);
    }

    private static Donation donation() {
        return new Donation(
                "10000000-0000-4000-8000-000000000001",
                "reader",
                "team",
                "10000000-0000-4000-8000-000000000002",
                "10000000-0000-4000-8000-000000000003",
                500,
                "Thanks",
                "10000000-0000-4000-8000-000000000004",
                "a".repeat(64),
                "b".repeat(64),
                Donation.Status.POSTED,
                Instant.EPOCH
        );
    }
}
