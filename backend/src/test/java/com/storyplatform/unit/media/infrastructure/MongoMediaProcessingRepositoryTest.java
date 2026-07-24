package com.storyplatform.unit.media.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.media.application.MediaProcessingOperations;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import com.storyplatform.media.infrastructure.persistence
        .MongoMediaAssetDocument;
import com.storyplatform.media.infrastructure.persistence
        .MongoMediaProcessingRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoMediaProcessingRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void claimIsAtomicAndIncludesLegacyAssetsWithoutAttemptField() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var repository = new MongoMediaProcessingRepository(mongo);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);

        assertThat(repository.claim(
                "worker-12345678",
                NOW,
                NOW.plusSeconds(30),
                5
        )).isEmpty();

        verify(mongo).findAndModify(
                query.capture(),
                any(Update.class),
                any(),
                eq(MongoMediaAssetDocument.class)
        );
        Document filter = query.getValue().getQueryObject();
        assertThat(filter.toString())
                .contains("PENDING_MODERATION")
                .contains("PROCESSING")
                .contains("processingAttempts")
                .contains("$exists")
                .contains("leaseUntil");
    }

    @Test
    void finalAttemptFailureBecomesPrivateAndTerminal() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoMediaAssetDocument.class)
        )).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var repository = new MongoMediaProcessingRepository(mongo);
        ArgumentCaptor<Update> update =
                ArgumentCaptor.forClass(Update.class);

        repository.reschedule(
                candidate(),
                "worker-12345678",
                "CLOUDINARY_IO_FAILURE",
                NOW.plusSeconds(30),
                true
        );

        verify(mongo).updateFirst(
                any(Query.class),
                update.capture(),
                eq(MongoMediaAssetDocument.class)
        );
        assertThat(update.getValue().getUpdateObject().toJson())
                .contains("PROCESSING_FAILED")
                .contains("PRIVATE")
                .contains("FAILED");
    }

    private static MediaProcessingOperations.Candidate candidate() {
        return new MediaProcessingOperations.Candidate(
                "asset", "source", 42, "jpg", "a".repeat(64),
                4, 10, 10, MediaOwnerType.USER, "owner",
                UploadPurpose.AVATAR, 5, NOW.plusSeconds(30)
        );
    }
}
