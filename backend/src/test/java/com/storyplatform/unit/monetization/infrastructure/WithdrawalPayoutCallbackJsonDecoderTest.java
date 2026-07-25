package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;
import com.storyplatform.monetization.infrastructure
        .WithdrawalPayoutCallbackJsonDecoder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalPayoutCallbackJsonDecoderTest {

    private final WithdrawalPayoutCallbackJsonDecoder decoder =
            new WithdrawalPayoutCallbackJsonDecoder(new ObjectMapper());

    @Test
    void decodesStrictCallbackPayload() {
        var callback = decoder.decode(body("""
                {
                  "eventId": "event-001",
                  "withdrawalId":
                    "10000000-0000-4000-8000-000000000001",
                  "providerReference": "provider-001",
                  "status": "PAID",
                  "errorCode": null,
                  "occurredAt": "2026-07-25T01:00:00Z"
                }
                """));

        assertThat(callback.eventId()).isEqualTo("event-001");
        assertThat(callback.status())
                .isEqualTo(WithdrawalPayoutGateway.Status.PAID);
        assertThat(callback.errorCode()).isNull();
    }

    @Test
    void rejectsUnknownFieldsInvalidStatusAndMissingValues() {
        assertInvalid("""
                {
                  "eventId": "event-001",
                  "withdrawalId": "withdrawal",
                  "providerReference": "provider-001",
                  "status": "PAID",
                  "errorCode": null,
                  "occurredAt": "2026-07-25T01:00:00Z",
                  "extra": true
                }
                """);
        assertInvalid("""
                {
                  "eventId": "event-001",
                  "withdrawalId": "withdrawal",
                  "providerReference": "provider-001",
                  "status": "UNKNOWN",
                  "errorCode": null,
                  "occurredAt": "2026-07-25T01:00:00Z"
                }
                """);
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> decoder.decode(body(value)))
                .isInstanceOf(WithdrawalException.class)
                .extracting("kind")
                .isEqualTo(WithdrawalException.Kind.INVALID);
    }

    private static byte[] body(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
