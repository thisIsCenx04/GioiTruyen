package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.PaymentWebhookException;
import com.storyplatform.monetization.application.port.PaymentEventDecoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Objects;

public final class PaymentEventJsonDecoder
        implements PaymentEventDecoder {

    private final ObjectMapper mapper;

    public PaymentEventJsonDecoder(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public DecodedPayment decode(byte[] rawBody) {
        try {
            JsonNode root = mapper.readTree(rawBody);
            if (root.size() != 6
                    || !"PAYMENT_RECEIVED".equals(
                    text(root, "eventType")
            )) {
                throw invalid();
            }
            return new DecodedPayment(
                    text(root, "eventId"),
                    text(root, "bankReference"),
                    root.path("amountVnd").asLong(-1),
                    text(root, "transferReference"),
                    Instant.parse(text(root, "occurredAt"))
            );
        } catch (PaymentWebhookException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private static String text(JsonNode root, String field) {
        String value = root.path(field).asText("");
        if (value.isBlank()) {
            throw invalid();
        }
        return value;
    }

    private static PaymentWebhookException invalid() {
        return new PaymentWebhookException(
                "Payment webhook payload is malformed.",
                PaymentWebhookException.Kind.INVALID
        );
    }
}
