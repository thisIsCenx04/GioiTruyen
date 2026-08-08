package com.storyplatform.media.application.port;

import com.storyplatform.media.application.MediaWebhookOperations;

public interface MediaAssetRepository {

    boolean recordPending(MediaWebhookOperations.AssetEvent event);
}
