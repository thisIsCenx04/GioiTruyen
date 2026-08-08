package com.storyplatform.notifications.application.port;

import java.time.Instant;

public interface UnsubscribeTokenCodec {

    String encode(Grant grant);

    Grant decode(String token);

    record Grant(
            String userId,
            NotificationPreferenceRepository.Channel channel,
            Instant expiresAt
    ) {
    }
}
