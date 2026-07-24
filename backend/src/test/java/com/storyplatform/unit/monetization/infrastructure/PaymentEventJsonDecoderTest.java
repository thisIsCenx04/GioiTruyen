package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.PaymentWebhookException;
import com.storyplatform.monetization.infrastructure.PaymentEventJsonDecoder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentEventJsonDecoderTest {

    private final PaymentEventJsonDecoder decoder =
            new PaymentEventJsonDecoder(new ObjectMapper());

    @Test
    void decodesTheMinimalSupportedPaymentContract() {
        String json = """
                {
                  "eventType": "PAYMENT_RECEIVED",
                  "eventId": "event-1",
                  "bankReference": "bank-1",
                  "amountVnd": 100000,
                  "transferReference": "GT12345678901234",
                  "occurredAt": "2026-07-25T00:00:00Z"
                }
                """;

        var decoded = decoder.decode(
                json.getBytes(StandardCharsets.UTF_8)
        );

        assertThat(decoded.amountVnd()).isEqualTo(100_000);
        assertThat(decoded.bankReference()).isEqualTo("bank-1");
    }

    @Test
    void rejectsMalformedAndUnsupportedEvents() {
        assertThatThrownBy(() -> decoder.decode(
                "{}".getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(PaymentWebhookException.class);
    }
}
