package com.storyplatform.monetization.application.port;

import java.time.Instant;

public interface WithdrawalPayoutCallbackDecoder {

    Callback decode(byte[] rawBody);

    record Callback(
            String eventId,
            String withdrawalId,
            String providerReference,
            WithdrawalPayoutGateway.Status status,
            String errorCode,
            Instant occurredAt
    ) {
    }
}
