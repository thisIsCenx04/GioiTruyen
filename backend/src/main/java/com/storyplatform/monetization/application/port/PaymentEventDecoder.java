package com.storyplatform.monetization.application.port;

import java.time.Instant;

public interface PaymentEventDecoder {

    DecodedPayment decode(byte[] rawBody);

    record DecodedPayment(
            String eventId,
            String bankReference,
            long amountVnd,
            String transferReference,
            Instant occurredAt
    ) {
    }
}
