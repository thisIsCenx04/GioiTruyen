package com.storyplatform.media.application.port;

import com.storyplatform.media.application.MediaWebhookOperations;

@FunctionalInterface
public interface CloudinaryNotificationDecoder {

    MediaWebhookOperations.AssetEvent decode(
            byte[] body,
            String eventId
    );
}
