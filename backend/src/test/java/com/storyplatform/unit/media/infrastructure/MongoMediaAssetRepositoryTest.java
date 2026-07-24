package com.storyplatform.unit.media.infrastructure;

import com.storyplatform.media.application.MediaWebhookOperations;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import com.storyplatform.media.infrastructure.persistence
        .MongoMediaAssetRepository;
import com.storyplatform.media.infrastructure.persistence
        .MongoWebhookReceipt;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoMediaAssetRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void atomicallyCreatesReceiptAndPendingAsset() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoMediaAssetRepository repository =
                new MongoMediaAssetRepository(mongo);

        assertThat(repository.recordPending(event())).isTrue();
        verify(mongo, times(2)).findAndModify(
                any(),
                any(),
                any(),
                any(Class.class)
        );
    }

    @Test
    void existingReceiptStopsReplayBeforeAssetMutation() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(),
                any(),
                any(),
                eq(MongoWebhookReceipt.class)
        )).thenReturn(new MongoWebhookReceipt("event", NOW));
        MongoMediaAssetRepository repository =
                new MongoMediaAssetRepository(mongo);

        assertThat(repository.recordPending(event())).isFalse();
        verify(mongo, times(1)).findAndModify(
                any(),
                any(),
                any(),
                any(Class.class)
        );
    }

    private static MediaWebhookOperations.AssetEvent event() {
        return new MediaWebhookOperations.AssetEvent(
                "event",
                "asset",
                "public",
                "00000000-0000-4000-8000-000000000003",
                MediaOwnerType.USER,
                "00000000-0000-4000-8000-000000000001",
                UploadPurpose.AVATAR,
                "image",
                "authenticated",
                "png",
                100,
                10,
                10,
                NOW
        );
    }
}
