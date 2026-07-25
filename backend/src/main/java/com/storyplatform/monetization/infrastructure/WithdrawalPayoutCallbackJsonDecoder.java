package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackDecoder;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Objects;

public final class WithdrawalPayoutCallbackJsonDecoder
        implements WithdrawalPayoutCallbackDecoder {

    private final ObjectMapper json;

    public WithdrawalPayoutCallbackJsonDecoder(ObjectMapper json) {
        this.json = Objects.requireNonNull(json);
    }

    @Override
    public Callback decode(byte[] rawBody) {
        try {
            JsonNode root = json.readTree(rawBody);
            if (root.size() != 6) {
                throw invalid();
            }
            String error = root.path("errorCode").isNull()
                    ? null
                    : text(root, "errorCode");
            return new Callback(
                    text(root, "eventId"),
                    text(root, "withdrawalId"),
                    text(root, "providerReference"),
                    WithdrawalPayoutGateway.Status.valueOf(
                            text(root, "status")
                    ),
                    error,
                    Instant.parse(text(root, "occurredAt"))
            );
        } catch (WithdrawalException exception) {
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

    private static WithdrawalException invalid() {
        return new WithdrawalException(
                "Payout callback payload is invalid.",
                WithdrawalException.Kind.INVALID
        );
    }
}
